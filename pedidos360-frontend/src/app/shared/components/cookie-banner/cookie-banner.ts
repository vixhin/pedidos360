import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';

@Component({
  selector: 'app-cookie-banner',
  standalone: true,
  templateUrl: './cookie-banner.html',
  styleUrl: './cookie-banner.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CookieBanner implements OnInit {
  private readonly STORAGE_KEY = 'pedidos360_cookie_consent';
  readonly isVisible = signal(false);
  readonly showDetails = signal(false);

  ngOnInit(): void {
    if (typeof window !== 'undefined' && typeof localStorage !== 'undefined') {
      const consent = localStorage.getItem(this.STORAGE_KEY);
      if (!consent) {
        // Un pequeño retraso para una animación suave de entrada
        setTimeout(() => this.isVisible.set(true), 600);
      }
    }
  }

  acceptAll(): void {
    this.saveConsent('all');
  }

  acceptNecessary(): void {
    this.saveConsent('necessary');
  }

  toggleDetails(): void {
    this.showDetails.update((v) => !v);
  }

  private saveConsent(level: 'all' | 'necessary'): void {
    if (typeof window !== 'undefined') {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(this.STORAGE_KEY, level);
        localStorage.setItem('pedidos360_cookie_timestamp', new Date().toISOString());
      }
      // Establecer cookie con vigencia de 1 año (31536000s)
      document.cookie = `pedidos360_consent=${level}; path=/; max-age=31536000; SameSite=Lax`;
    }
    this.isVisible.set(false);
  }
}

