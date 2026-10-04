import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, of } from 'rxjs';
import { Client, StompSubscription } from '@stomp/stompjs';
import { BackendUrlService } from './backend-url.service';
import { API_CONFIG } from '../config/api.config';
import { AuthService } from './auth.service';

export interface MensajeChat {
  id: number;
  conversacionId: number;
  remitenteId: number;
  tipoRemitente: 'CLIENTE' | 'REPARTIDOR';
  contenido: string;
  estado: 'ENVIADO' | 'ENTREGADO' | 'LEIDO' | 'FALLIDO';
  fechaEnvio: string;
}

export interface ConversacionChat {
  id: number;
  pedidoId: number;
  clienteId: number;
  repartidorId: number;
  estado: 'ABIERTO' | 'CERRADO' | 'ELIMINADO';
  fechaCreacion: string;
  fechaCierre?: string;
  mensajes: MensajeChat[];
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly urls = inject(BackendUrlService);
  private readonly auth = inject(AuthService);

  readonly conversacionActiva = signal<ConversacionChat | null>(null);
  readonly mensajes = signal<MensajeChat[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private stompClient: Client | null = null;
  private topicSub: StompSubscription | null = null;
  private statusSub: StompSubscription | null = null;

  /**
   * Obtiene o crea la conversación de chat para un pedido específico
   */
  abrirChatDePedido(pedidoId: number, clienteId: number, repartidorId: number): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const url = `${this.urls.base('chat')}/conversacion?pedidoId=${pedidoId}&clienteId=${clienteId}&repartidorId=${repartidorId}`;
    this.http
      .post<ConversacionChat>(url, {})
      .pipe(
        catchError((err) => {
          console.error('[ChatService] Error al abrir chat:', err);
          this.errorMessage.set('No se pudo conectar con el servicio de chat.');
          return of(null);
        })
      )
      .subscribe((conv) => {
        this.isLoading.set(false);
        if (conv) {
          this.conversacionActiva.set(conv);
          this.mensajes.set(conv.mensajes || []);
          this.conectarWebSocket(conv.id);
        }
      });
  }

  /**
   * Envía un mensaje en la conversación activa vía HTTP REST + WebSocket STOMP
   */
  enviarMensaje(conversacionId: number, remitenteId: number, tipoRemitente: 'CLIENTE' | 'REPARTIDOR', contenido: string): void {
    if (!contenido || !contenido.trim()) return;

    const payload = {
      conversacionId,
      remitenteId,
      tipoRemitente,
      contenido: contenido.trim(),
    };

    this.http
      .post<MensajeChat>(`${this.urls.base('chat')}/mensaje`, payload)
      .pipe(
        catchError((err) => {
          console.error('[ChatService] Error al enviar mensaje:', err);
          const msg = err.error?.message || 'No se pudo enviar el mensaje.';
          alert(msg);
          return of(null);
        })
      )
      .subscribe((nuevoMsg) => {
        if (nuevoMsg) {
          this.mensajes.update((list) => {
            if (list.some((m) => m.id === nuevoMsg.id)) return list;
            return [...list, nuevoMsg];
          });
        }
      });
  }

  /**
   * Conecta al WebSocket en tiempo real utilizando la librería oficial @stomp/stompjs
   */
  private conectarWebSocket(conversacionId: number): void {
    this.desconectar();

    let wsUrl = API_CONFIG.chatWs;
    if (window.location.protocol === 'https:') {
      wsUrl = wsUrl.replace(/^ws:\/\//, 'wss://').replace(/^http:\/\//, 'wss://');
    } else {
      wsUrl = wsUrl.replace(/^http:\/\//, 'ws://');
    }

    const token = this.auth.token();
    const currentUserId = this.auth.user()?.id;
    const currentUserEmail = this.auth.user()?.email;

    const connectHeaders: Record<string, string> = {
      'X-User-Id': currentUserId ? String(currentUserId) : '',
      'X-User-Email': currentUserEmail || '',
    };
    if (token) {
      connectHeaders['Authorization'] = `Bearer ${token}`;
    }

    this.stompClient = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectHeaders,
      onConnect: () => {
        console.log('[ChatService] Conectado exitosamente vía STOMP');

        this.topicSub = this.stompClient?.subscribe(`/topic/chat/${conversacionId}`, (message) => {
          try {
            const data: MensajeChat = JSON.parse(message.body);
            if (data.id && data.contenido) {
              this.mensajes.update((list) => {
                if (list.some((m) => m.id === data.id)) return list;
                return [...list, data];
              });
            }
          } catch (e) {
            console.error('[ChatService] Error procesando mensaje STOMP:', e);
          }
        }) || null;

        this.statusSub = this.stompClient?.subscribe(`/topic/chat/${conversacionId}/status`, (message) => {
          try {
            const data = JSON.parse(message.body);
            if (data.tipo === 'CHAT_CERRADO') {
              this.conversacionActiva.update((c) => (c ? { ...c, estado: 'CERRADO' } : null));
            }
          } catch (e) {
            console.error('[ChatService] Error procesando evento de cierre STOMP:', e);
          }
        }) || null;
      },
      onStompError: (frame) => {
        console.error('[ChatService] STOMP Error:', frame.headers['message']);
      },
      onWebSocketClose: () => {
        console.log('[ChatService] Conexión WebSocket STOMP cerrada');
      },
    });

    this.stompClient.activate();
  }

  /**
   * Cancela las suscripciones y desactiva el cliente STOMP
   */
  desconectar(): void {
    if (this.topicSub) {
      try { this.topicSub.unsubscribe(); } catch {}
      this.topicSub = null;
    }
    if (this.statusSub) {
      try { this.statusSub.unsubscribe(); } catch {}
      this.statusSub = null;
    }
    if (this.stompClient) {
      try { this.stompClient.deactivate(); } catch {}
      this.stompClient = null;
    }
  }
}
