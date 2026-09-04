import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { MsalService } from '@azure/msal-angular';
import { Header } from './layout/header/header';
import { Footer } from './layout/footer/footer';
import { AuthService } from './core/services/auth.service';
import { isAzureAdConfigured } from './core/config/auth.config';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, Footer],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  private readonly noChromeRoutes = ['/login'];
  readonly showChrome = signal(true);

  constructor(
    private router: Router,
    private msal: MsalService,
    private auth: AuthService
  ) {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.showChrome.set(!this.noChromeRoutes.includes(e.urlAfterRedirects));
      });
  }

  ngOnInit(): void {
    if (!isAzureAdConfigured()) return;
    // Procesa la respuesta del redirect de Microsoft Entra ID (OIDC) al volver a la app.
    this.msal.handleRedirectObservable().subscribe({
      next: () => this.auth.syncFromMsal(),
      error: (err) => console.error('[MSAL] Error procesando el redirect', err),
    });
  }
}
