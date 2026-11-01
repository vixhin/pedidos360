import { ChangeDetectionStrategy, Component, computed, signal, ViewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';
import { ApiResponse } from '../../core/models/auth.model';
import { isAzureAdConfigured } from '../../core/config/auth.config';
import { TurnstileComponent } from '../../shared/components/turnstile/turnstile.component';

type Mode = 'login' | 'register' | 'forgot';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, Icon, FormsModule, TurnstileComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  readonly mode = signal<Mode>('login');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  // Login
  readonly loginEmail = signal('');
  readonly loginPassword = signal('');
  readonly showLoginPassword = signal(false);
  readonly loginTurnstileToken = signal<string | null>(null);

  // Recuperar contraseña
  readonly forgotEmail = signal('');
  readonly forgotSuccess = signal(false);
  readonly forgotMessage = signal<string | null>(null);

  // Registro
  readonly registerNombre = signal('');
  readonly registerEmail = signal('');
  readonly registerTelefono = signal('');
  readonly registerPassword = signal('');
  readonly registerConfirmPassword = signal('');
  readonly showRegisterPassword = signal(false);
  readonly showRegisterConfirmPassword = signal(false);
  readonly registerTurnstileToken = signal<string | null>(null);
  readonly acceptTerms = signal(false);
  readonly showTermsModal = signal(false);

  // Reglas de contraseña
  readonly hasMinLength = computed(() => this.registerPassword().length >= 8);
  readonly hasUpperCase = computed(() => /[A-Z]/.test(this.registerPassword()));
  readonly hasNumber = computed(() => /[0-9]/.test(this.registerPassword()));
  readonly hasSpecialChar = computed(() => /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?~`]/.test(this.registerPassword()));
  readonly passwordsMatch = computed(() => {
    const p = this.registerPassword();
    const c = this.registerConfirmPassword();
    return !c || p === c;
  });
  readonly isPasswordStrong = computed(() =>
    this.hasMinLength() && this.hasUpperCase() && this.hasNumber() && this.hasSpecialChar()
  );

  @ViewChild('loginTurnstile') loginTurnstile?: TurnstileComponent;
  @ViewChild('registerTurnstile') registerTurnstile?: TurnstileComponent;

  constructor(private auth: AuthService, private router: Router) {}

  toggleShowLoginPassword(): void {
    this.showLoginPassword.update((v) => !v);
  }

  toggleShowRegisterPassword(): void {
    this.showRegisterPassword.update((v) => !v);
  }

  toggleShowRegisterConfirmPassword(): void {
    this.showRegisterConfirmPassword.update((v) => !v);
  }

  readonly azureAdConfigured = isAzureAdConfigured();

  /**
   * Dispara el flujo OIDC "Authorization Code + PKCE" vía MSAL.
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
    this.forgotSuccess.set(false);
    this.forgotMessage.set(null);
    this.acceptTerms.set(false);
    this.loginTurnstileToken.set(null);
    this.registerTurnstileToken.set(null);
  }

  onLoginTurnstileResolved(token: string): void {
    this.loginTurnstileToken.set(token);
    if (this.error()?.includes('Turnstile')) {
      this.error.set(null);
    }
  }

  onLoginTurnstileExpired(): void {
    this.loginTurnstileToken.set(null);
  }

  onRegisterTurnstileResolved(token: string): void {
    this.registerTurnstileToken.set(token);
    if (this.error()?.includes('Turnstile')) {
      this.error.set(null);
    }
  }

  onRegisterTurnstileExpired(): void {
    this.registerTurnstileToken.set(null);
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

    const token = this.loginTurnstileToken();
    if (!token) {
      this.error.set('Por favor completa la verificación de seguridad Cloudflare Turnstile.');
      return;
    }

    this.loading.set(true);
    this.auth.loginWithCredentials(this.loginEmail(), this.loginPassword(), token).subscribe({
      next: () => {
        this.loading.set(false);
        this.redirectByRole();
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loginTurnstileToken.set(null);
        this.loginTurnstile?.reset();
        this.error.set(this.extractMessage(err, 'No se pudo iniciar sesión. Verifica tus credenciales.'));
      },
    });
  }

  submitForgotPassword(): void {
    this.error.set(null);
    this.forgotMessage.set(null);

    const email = this.forgotEmail().trim();
    if (!email) {
      this.error.set('Por favor ingresa tu correo electrónico registrado.');
      return;
    }

    this.loading.set(true);
    this.auth.forgotPassword(email).subscribe({
      next: () => {
        this.loading.set(false);
        this.forgotSuccess.set(true);
        this.forgotMessage.set('Si el correo ingresado se encuentra registrado, te hemos enviado instrucciones para restablecer tu contraseña de forma segura.');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.extractMessage(err, 'No se pudo procesar la solicitud de recuperación. Inténtalo de nuevo.'));
      }
    });
  }

  submitRegister(): void {
    this.error.set(null);

    if (!this.acceptTerms()) {
      this.error.set('Debes leer y aceptar los Términos y Condiciones para registrarte.');
      return;
    }

    if (this.registerPassword() !== this.registerConfirmPassword()) {
      this.error.set('Las contraseñas no coinciden. Por favor verifícalas.');
      return;
    }

    if (!this.isPasswordStrong()) {
      this.error.set('La contraseña debe tener al menos 8 caracteres, incluir una mayúscula, un número y un carácter especial.');
      return;
    }

    const token = this.registerTurnstileToken();
    if (!token) {
      this.error.set('Por favor completa la verificación de seguridad Cloudflare Turnstile.');
      return;
    }

    this.loading.set(true);
    this.auth.register(
      this.registerNombre(),
      this.registerEmail(),
      this.registerPassword(),
      token,
      this.registerTelefono().trim() || undefined
    ).subscribe({
      next: () => {
        this.auth.loginWithCredentials(this.registerEmail(), this.registerPassword(), token).subscribe({
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
        this.registerTurnstileToken.set(null);
        this.registerTurnstile?.reset();
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
