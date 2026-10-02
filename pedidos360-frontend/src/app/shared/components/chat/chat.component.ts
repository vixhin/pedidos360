import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Icon } from '../../icon/icon';
import { ChatService } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, Icon, FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css',
})
export class ChatComponent implements OnInit, OnChanges, OnDestroy {
  readonly chatSvc = inject(ChatService);
  readonly auth = inject(AuthService);

  readonly pedidoId = input.required<number>();
  readonly clienteId = input.required<number>();
  readonly repartidorId = input.required<number>();

  readonly nuevoMensaje = signal('');

  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLDivElement>;

  ngOnInit(): void {
    this.iniciarChat();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['pedidoId'] && !changes['pedidoId'].firstChange) {
      this.iniciarChat();
    }
  }

  ngOnDestroy(): void {
    this.chatSvc.desconectar();
  }

  private iniciarChat(): void {
    if (this.pedidoId() && this.clienteId() && this.repartidorId()) {
      this.chatSvc.abrirChatDePedido(this.pedidoId(), this.clienteId(), this.repartidorId());
    }
  }

  enviar(): void {
    const text = this.nuevoMensaje().trim();
    const conv = this.chatSvc.conversacionActiva();
    const user = this.auth.user();

    if (!text || !conv || !user?.id) return;

    const tipoRemitente = user.id === conv.clienteId ? 'CLIENTE' : 'REPARTIDOR';
    this.chatSvc.enviarMensaje(conv.id, user.id, tipoRemitente, text);
    this.nuevoMensaje.set('');
    setTimeout(() => this.scrollToBottom(), 100);
  }

  private scrollToBottom(): void {
    try {
      if (this.scrollContainer) {
        this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
      }
    } catch {}
  }
}
