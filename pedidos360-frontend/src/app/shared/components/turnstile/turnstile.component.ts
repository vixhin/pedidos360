import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  inject,
  PLATFORM_ID
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { environment } from '../../../../environments/environment';

declare global {
  interface Window {
    turnstile?: {
      render: (
        container: HTMLElement,
        options: {
          sitekey: string;
          theme?: 'auto' | 'light' | 'dark';
          callback: (token: string) => void;
          'error-callback'?: (errorCode: string) => void;
          'expired-callback'?: () => void;
        }
      ) => string;
      reset: (widgetId: string) => void;
      remove: (widgetId: string) => void;
    };
  }
}

@Component({
  selector: 'app-turnstile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turnstile.component.html',
  styleUrls: ['./turnstile.component.css']
})
export class TurnstileComponent implements OnInit, OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);

  @Input() siteKey: string = environment.turnstile?.siteKey || '1x00000000000000000000AA';
  @Input() theme: 'auto' | 'light' | 'dark' = 'auto';

  @Output() resolved = new EventEmitter<string>();
  @Output() error = new EventEmitter<string>();
  @Output() expired = new EventEmitter<void>();

  @ViewChild('turnstileContainer', { static: true })
  containerRef!: ElementRef<HTMLDivElement>;

  widgetId: string | null = null;

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadScriptAndRender();
    }
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId) && this.widgetId && window.turnstile) {
      try {
        window.turnstile.remove(this.widgetId);
      } catch {
        // Silencioso si el elemento ya fue retirado del árbol DOM
      }
    }
  }

  public reset(): void {
    if (isPlatformBrowser(this.platformId) && this.widgetId && window.turnstile) {
      try {
        window.turnstile.reset(this.widgetId);
      } catch (e) {
        console.warn('[Turnstile] Error al reiniciar widget:', e);
      }
    }
  }

  private loadScriptAndRender(): void {
    if (window.turnstile) {
      this.renderWidget();
      return;
    }

    const scriptId = 'cloudflare-turnstile-script';
    let script = document.getElementById(scriptId) as HTMLScriptElement;

    if (!script) {
      script = document.createElement('script');
      script.id = scriptId;
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }

    const checkInterval = setInterval(() => {
      if (window.turnstile) {
        clearInterval(checkInterval);
        this.renderWidget();
      }
    }, 100);

    setTimeout(() => clearInterval(checkInterval), 10000);
  }

  private renderWidget(): void {
    if (!window.turnstile || !this.containerRef?.nativeElement || this.widgetId) {
      return;
    }

    try {
      this.widgetId = window.turnstile.render(this.containerRef.nativeElement, {
        sitekey: this.siteKey,
        theme: this.theme,
        callback: (token: string) => {
          this.resolved.emit(token);
        },
        'error-callback': (code: string) => {
          console.warn('[Turnstile] Verificación fallida code:', code);
          this.error.emit(code);
        },
        'expired-callback': () => {
          console.warn('[Turnstile] Token expirado.');
          this.expired.emit();
        }
      });
    } catch (err) {
      console.error('[Turnstile] Error al renderizar widget:', err);
    }
  }
}

