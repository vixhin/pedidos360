import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, of, tap } from 'rxjs';
import { BackendUrlService } from './backend-url.service';
import { API_CONFIG } from '../config/api.config';

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

  readonly conversacionActiva = signal<ConversacionChat | null>(null);
  readonly mensajes = signal<MensajeChat[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private socket: WebSocket | null = null;

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
   * Envía un mensaje en la conversación activa vía HTTP REST + WebSocket
   */
  enviarMensaje(conversacionId: number, remitenteId: number, tipoRemitente: 'CLIENTE' | 'REPARTIDOR', contenido: string): void {
    if (!contenido.trim()) return;

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
          // Si el WS no lo ha insertado aún, agregarlo a la lista
          this.mensajes.update((list) => {
            if (list.some((m) => m.id === nuevoMsg.id)) return list;
            return [...list, nuevoMsg];
          });
        }
      });
  }

  /**
   * Conecta al WebSocket en tiempo real mediante el endpoint STOMP/WS
   */
  private conectarWebSocket(conversacionId: number): void {
    if (this.socket) {
      try { this.socket.close(); } catch {}
    }

    try {
      const wsUrl = API_CONFIG.chatWs.replace('http', 'ws');
      this.socket = new WebSocket(wsUrl + '/websocket');

      this.socket.onopen = () => {
        // Enviar frame de conexión STOMP
        this.socket?.send("CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000");
        // Suscribirse al tópico de la conversación
        const subFrame = `SUBSCRIBE\nid:sub-0\ndestination:/topic/chat/${conversacionId}\n\n\u0000`;
        this.socket?.send(subFrame);
      };

      this.socket.onmessage = (event) => {
        const bodyStr = event.data as string;
        if (bodyStr.includes('MESSAGE')) {
          try {
            const jsonStart = bodyStr.indexOf('{');
            const jsonEnd = bodyStr.lastIndexOf('}');
            if (jsonStart !== -1 && jsonEnd !== -1) {
              const jsonStr = bodyStr.substring(jsonStart, jsonEnd + 1);
              const data = JSON.parse(jsonStr);

              if (data.id && data.contenido) {
                this.mensajes.update((list) => {
                  if (list.some((m) => m.id === data.id)) return list;
                  return [...list, data];
                });
              } else if (data.tipo === 'CHAT_CERRADO') {
                this.conversacionActiva.update((c) => (c ? { ...c, estado: 'CERRADO' } : null));
              }
            }
          } catch (e) {
            console.debug('[ChatService] WS Frame Parse:', e);
          }
        }
      };
    } catch (err) {
      console.warn('[ChatService] Fallback WS Connection:', err);
    }
  }

  /**
   * Cierra el socket al destruir la vista o cambiar de pedido
   */
  desconectar(): void {
    if (this.socket) {
      try { this.socket.close(); } catch {}
      this.socket = null;
    }
  }
}
