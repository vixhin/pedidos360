import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface CookiePreferences {
  necessary: boolean;
  analytics: boolean;
  preferences: boolean;
}

@Component({
  selector: 'app-cookie-banner',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './cookie-banner.html',
  styleUrl: './cookie-banner.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CookieBanner implements OnInit {
  private readonly STORAGE_KEY = 'pedidos360_cookie_consent';
  readonly isVisible = signal(false);
  readonly showDetails = signal(false);

  readonly allowAnalytics = signal(false);
  readonly allowPreferences = signal(false);

  ngOnInit(): void {
    if (typeof window !== 'undefined' && typeof localStorage !== 'undefined') {
      const consent = localStorage.getItem(this.STORAGE_KEY);
      if (!consent) {
        setTimeout(() => this.isVisible.set(true), 600);
      } else {
        try {
          const parsed = JSON.parse(consent);
          if (parsed && typeof parsed === 'object') {
            this.allowAnalytics.set(!!parsed.analytics);
            this.allowPreferences.set(!!parsed.preferences);
          } else if (consent === 'all') {
            this.allowAnalytics.set(true);
            this.allowPreferences.set(true);
          }
        } catch {
          if (consent === 'all') {
            this.allowAnalytics.set(true);
            this.allowPreferences.set(true);
          }
        }
      }

      // Escuchar evento global para reabrir panel de cookies desde pie de página o configuración
      window.addEventListener('p360:open-cookie-preferences', () => {
        this.showDetails.set(true);
        this.isVisible.set(true);
      });
    }
  }

  acceptAll(): void {
    this.allowAnalytics.set(true);
    this.allowPreferences.set(true);
    this.saveConsent({ necessary: true, analytics: true, preferences: true });
  }

  acceptNecessary(): void {
    this.allowAnalytics.set(false);
    this.allowPreferences.set(false);
    this.saveConsent({ necessary: true, analytics: false, preferences: false });
  }

  saveCustomPreferences(): void {
    this.saveConsent({
      necessary: true,
      analytics: this.allowAnalytics(),
      preferences: this.allowPreferences()
    });
  }

  toggleDetails(): void {
    this.showDetails.update((v) => !v);
  }

  private saveConsent(prefs: CookiePreferences): void {
    if (typeof window !== 'undefined') {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(this.STORAGE_KEY, JSON.stringify(prefs));
        localStorage.setItem('pedidos360_cookie_timestamp', new Date().toISOString());
      }
      const level = (prefs.analytics && prefs.preferences) ? 'all' : (prefs.analytics || prefs.preferences) ? 'custom' : 'necessary';
      document.cookie = `pedidos360_consent=${level}; path=/; max-age=31536000; SameSite=Lax`;
    }
    this.isVisible.set(false);
  }
}
