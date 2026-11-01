import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';
import { ApiResponse } from '../../core/models/auth.model';
import { isAzureAdConfigured } from '../../core/config/auth.config';
import { Recaptcha } from '../../shared/components/recaptcha/recaptcha';

type Mode = 'login' | 'register';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, Icon, FormsModule, Recaptcha],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  readonly mode = signal<Mode>('login');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly loginEmail = signal('');
  readonly loginPassword = signal('');
  readonly showLoginPassword = signal(false);

  readonly registerNombre = signal('');
  readonly registerEmail = signal('');
  readonly registerPassword = signal('');
  readonly showRegisterPassword = signal(false);
  readonly acceptTerms = signal(false);
  readonly captchaVerified = signal(false);
  readonly loginCaptchaVerified = signal(false);
  readonly showTermsModal = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  toggleShowLoginPassword(): void {
    this.showLoginPassword.update((v) => !v);
  }

  toggleShowRegisterPassword(): void {
    this.showRegisterPassword.update((v) => !v);
  }

  readonly azureAdConfigured = isAzureAdConfigured();

  /**
   * Dispara el flujo OIDC "Authorization Code + PKCE" vía MSAL. Redirige a
   * Microsoft, así que no hay navegación local que hacer aquí.
   */
  signInWithMicrosoft(): void {
    if (!this.azureAdConfigured) {
      this.error.set(
        'Microsoft Entra ID aún no está configurado en este ambiente (falta clientId/tenantId en auth.config.ts).'
      );
      return;
    }
    this.error.set(null);
    this.auth.loginWithMicrosoft();
  }

  setMode(mode: Mode): void {
    this.mode.set(mode);
    this.error.set(null);
    this.acceptTerms.set(false);
    this.captchaVerified.set(false);
    this.loginCaptchaVerified.set(false);
  }
  
  onLoginCaptchaVerified(verified: boolean): void {
    this.loginCaptchaVerified.set(verified);
    if (verified && this.error()?.includes('reCAPTCHA')) {
      this.error.set(null);
    }
  }
  onCaptchaVerified(verified: boolean): void {
    this.captchaVerified.set(verified);
    if (verified && this.error()?.includes('reCAPTCHA')) {
      this.error.set(null);
    }
  }

  openTermsModal(event?: Event): void {
    if (event) event.preventDefault();
    this.showTermsModal.set(true);
  }

  closeTermsModal(): void {
    this.showTermsModal.set(false);
  }

  acceptTermsFromModal(): void {
    this.acceptTerms.set(true);
    this.showTermsModal.set(false);
    if (this.error()?.includes('Términos')) {
      this.error.set(null);
    }
  }


  submitLogin(): void {
    this.error.set(null);

    if (!this.loginCaptchaVerified()) {
      this.error.set('Por favor completa la verificación de seguridad reCAPTCHA (No soy un robot).');
      return;
    }

    this.loading.set(true);
    this.auth.loginWithCredentials(this.loginEmail(), this.loginPassword()).subscribe({
      next: () => {
        this.loading.set(false);
        this.redirectByRole();
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.extractMessage(err, 'No se pudo iniciar sesión. Verifica tus credenciales.'));
      },
    });
  }

  submitRegister(): void {
    this.error.set(null);

    if (!this.acceptTerms()) {
      this.error.set('Debes leer y aceptar los Términos y Condiciones para registrarte.');
      return;
    }

    if (!this.captchaVerified()) {
      this.error.set('Por favor completa la verificación de seguridad reCAPTCHA (No soy un robot).');
      return;
    }

    this.loading.set(true);
    this.auth.register(this.registerNombre(), this.registerEmail(), this.registerPassword()).subscribe({
      next: () => {
        this.auth.loginWithCredentials(this.registerEmail(), this.registerPassword()).subscribe({
          next: () => {
            this.loading.set(false);
            this.redirectByRole();
          },
          error: () => {
            this.loading.set(false);
            this.setMode('login');
            this.loginEmail.set(this.registerEmail());
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.extractMessage(err, 'No se pudo crear la cuenta.'));
      },
    });
  }

  private redirectByRole(): void {
    const role = this.auth.activeRole();
    if (role === 'ADMIN') {
      this.router.navigate(['/analitica']);
    } else if (role === 'VENDEDOR') {
      this.router.navigate(['/vendedor']);
    } else if (role === 'REPARTIDOR') {
      this.router.navigate(['/repartidor']);
    } else {
      this.router.navigate(['/']);
    }
  }

  private extractMessage(err: HttpErrorResponse, fallback: string): string {
    const body = err.error as ApiResponse<unknown> | undefined;
    if (body?.message) return body.message;
    if (err.status === 0) return 'No se pudo conectar con el servidor. ¿Está corriendo el backend (docker-compose)?';
    return fallback;
  }
}
