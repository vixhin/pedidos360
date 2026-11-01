import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';

export interface CaptchaTile {
  id: number;
  label: string;
  isTarget: boolean;
  type: string;
}

export interface CaptchaChallenge {
  id: string;
  title: string;
  targetSubject: string;
  hint: string;
  tiles: CaptchaTile[];
}

@Component({
  selector: 'app-recaptcha',
  standalone: true,
  templateUrl: './recaptcha.html',
  styleUrl: './recaptcha.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Recaptcha {
  @Input() theme: 'light' | 'dark' = 'light';
  @Output() verified = new EventEmitter<boolean>();

  readonly isChecking = signal(false);
  readonly isVerified = signal(false);
  readonly showChallengeModal = signal(false);
  readonly currentChallengeIndex = signal(0);
  readonly selectedTileIds = signal<Set<number>>(new Set());
  readonly challengeError = signal<string | null>(null);
  readonly isEvaluating = signal(false);

  readonly challenges: CaptchaChallenge[] = [
    {
      id: 'vehicles',
      title: 'Selecciona todas las imágenes con',
      targetSubject: 'vehículos de reparto o despacho',
      hint: 'Haz clic en verificar cuando hayas marcado todos.',
      tiles: [
        { id: 1, label: 'Furgón de reparto 360', isTarget: true, type: 'van' },
        { id: 2, label: 'Semáforo de tránsito', isTarget: false, type: 'traffic-light' },
        { id: 3, label: 'Moto de delivery exprés', isTarget: true, type: 'motorcycle' },
        { id: 4, label: 'Manzana fresca', isTarget: false, type: 'apple' },
        { id: 5, label: 'Camión de carga', isTarget: true, type: 'truck' },
        { id: 6, label: 'Paso peatonal', isTarget: false, type: 'pedestrian' },
        { id: 7, label: 'Bicicleta de despacho', isTarget: true, type: 'bicycle' },
        { id: 8, label: 'Bolsa de compras', isTarget: false, type: 'bag' },
        { id: 9, label: 'Hidrante de agua', isTarget: false, type: 'hydrant' },
      ],
    },
    {
      id: 'traffic',
      title: 'Selecciona todas las imágenes con',
      targetSubject: 'semáforos',
      hint: 'Si no hay ninguna, haz clic en omitir.',
      tiles: [
        { id: 1, label: 'Semáforo verde', isTarget: true, type: 'traffic-light' },
        { id: 2, label: 'Autobús de pasajeros', isTarget: false, type: 'bus' },
        { id: 3, label: 'Semáforo en rojo', isTarget: true, type: 'traffic-light' },
        { id: 4, label: 'Paso peatonal', isTarget: false, type: 'pedestrian' },
        { id: 5, label: 'Bicicleta', isTarget: false, type: 'bicycle' },
        { id: 6, label: 'Semáforo peatonal', isTarget: true, type: 'traffic-light' },
        { id: 7, label: 'Automóvil particular', isTarget: false, type: 'car' },
        { id: 8, label: 'Furgón de reparto', isTarget: false, type: 'van' },
        { id: 9, label: 'Semáforo con poste', isTarget: true, type: 'traffic-light' },
      ],
    },
    {
      id: 'fresh-food',
      title: 'Selecciona todas las imágenes con',
      targetSubject: 'frutas o productos frescos',
      hint: 'Haz clic en verificar cuando no quede ninguno.',
      tiles: [
        { id: 1, label: 'Manzanas rojas', isTarget: true, type: 'apple' },
        { id: 2, label: 'Camión de carga', isTarget: false, type: 'truck' },
        { id: 3, label: 'Plátanos maduros', isTarget: true, type: 'banana' },
        { id: 4, label: 'Bebida gaseosa', isTarget: false, type: 'bottle' },
        { id: 5, label: 'Naranjas de jugo', isTarget: true, type: 'orange' },
        { id: 6, label: 'Semáforo', isTarget: false, type: 'traffic-light' },
        { id: 7, label: 'Cesta de verduras', isTarget: true, type: 'vegetables' },
        { id: 8, label: 'Moto de reparto', isTarget: false, type: 'motorcycle' },
        { id: 9, label: 'Detergente para ropa', isTarget: false, type: 'soap' },
      ],
    },
  ];

  get currentChallenge(): CaptchaChallenge {
    return this.challenges[this.currentChallengeIndex()];
  }

  handleCheckboxClick(): void {
    if (this.isVerified()) return;

    this.isChecking.set(true);

    // Abre el modal interactivo de reconocimiento visual
    setTimeout(() => {
      this.isChecking.set(false);
      this.selectedTileIds.set(new Set());
      this.challengeError.set(null);
      this.showChallengeModal.set(true);
    }, 300);
  }

  toggleTile(tileId: number): void {
    const current = new Set(this.selectedTileIds());
    if (current.has(tileId)) {
      current.delete(tileId);
    } else {
      current.add(tileId);
    }
    this.selectedTileIds.set(current);
    if (this.challengeError()) {
      this.challengeError.set(null);
    }
  }

  isTileSelected(tileId: number): boolean {
    return this.selectedTileIds().has(tileId);
  }

  refreshChallenge(): void {
    const nextIdx = (this.currentChallengeIndex() + 1) % this.challenges.length;
    this.currentChallengeIndex.set(nextIdx);
    this.selectedTileIds.set(new Set());
    this.challengeError.set(null);
  }

  verifyChallenge(): void {
    const challenge = this.currentChallenge;
    const selected = this.selectedTileIds();

    const targetTiles = challenge.tiles.filter((t) => t.isTarget);
    const nonTargetTiles = challenge.tiles.filter((t) => !t.isTarget);

    const hasSelectedAllTargets = targetTiles.every((t) => selected.has(t.id));
    const hasSelectedAnyNonTarget = nonTargetTiles.some((t) => selected.has(t.id));

    this.isEvaluating.set(true);

    setTimeout(() => {
      this.isEvaluating.set(false);

      if (hasSelectedAllTargets && !hasSelectedAnyNonTarget && selected.size > 0) {
        // Validación exitosa
        this.showChallengeModal.set(false);
        this.isVerified.set(true);
        this.verified.emit(true);
      } else {
        // Si seleccionó al menos 2 objetivos y ningún no-objetivo, también aprobarlo para excelente UX
        const targetsSelectedCount = targetTiles.filter((t) => selected.has(t.id)).length;
        if (targetsSelectedCount >= 2 && !hasSelectedAnyNonTarget) {
          this.showChallengeModal.set(false);
          this.isVerified.set(true);
          this.verified.emit(true);
          return;
        }

        // Falló: pide seleccionar nuevamente
        this.challengeError.set('Por favor, selecciona todas las imágenes correspondientes.');
        if (selected.size === 0) {
          this.challengeError.set('Debes seleccionar al menos una imagen correspondiente.');
        }
      }
    }, 350);
  }

  closeChallenge(): void {
    this.showChallengeModal.set(false);
    this.isChecking.set(false);
  }

  reset(): void {
    this.isChecking.set(false);
    this.isVerified.set(false);
    this.showChallengeModal.set(false);
    this.selectedTileIds.set(new Set());
    this.verified.emit(false);
  }
}
