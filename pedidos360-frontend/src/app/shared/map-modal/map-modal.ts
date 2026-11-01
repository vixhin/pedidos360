import { ChangeDetectionStrategy, Component, EventEmitter, OnInit, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Icon } from '../icon/icon';
import { CoberturaComuna, PedidoService } from '../../core/services/pedido.service';

export interface Sector {
  id: string;
  name: string;
  eta: string;
  active: boolean;
  pinX: number;
  pinY: number;
}

const PIN_COORDINATES: Record<string, { x: number; y: number }> = {
  'santiago': { x: 48, y: 46 },
  'santiago centro': { x: 48, y: 46 },
  'providencia': { x: 58, y: 38 },
  'las condes': { x: 72, y: 30 },
  'ñuñoa': { x: 62, y: 52 },
  'nunoa': { x: 62, y: 52 },
  'san miguel': { x: 46, y: 64 },
  'maipú': { x: 28, y: 62 },
  'maipu': { x: 28, y: 62 },
  'la florida': { x: 68, y: 70 },
  'macul': { x: 60, y: 58 }
};

@Component({
  selector: 'app-map-modal',
  standalone: true,
  imports: [Icon, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './map-modal.html',
  styleUrl: './map-modal.css',
})
export class MapModal implements OnInit {
  private readonly pedidoSvc = inject(PedidoService);

  @Output() close = new EventEmitter<void>();
  @Output() selectLocation = new EventEmitter<string>();

  readonly sectors = signal<Sector[]>([
    { id: 'santiago-centro', name: 'Santiago Centro', eta: '25-40 min', active: true, pinX: 48, pinY: 46 },
    { id: 'providencia', name: 'Providencia', eta: '20-35 min', active: true, pinX: 58, pinY: 38 },
    { id: 'las-condes', name: 'Las Condes', eta: '25-45 min', active: true, pinX: 72, pinY: 30 },
    { id: 'nunoa', name: 'Ñuñoa', eta: '20-30 min', active: true, pinX: 62, pinY: 52 },
    { id: 'san-miguel', name: 'San Miguel', eta: '15-30 min', active: true, pinX: 46, pinY: 64 },
    { id: 'maipu', name: 'Maipú', eta: '30-50 min', active: true, pinX: 28, pinY: 62 },
  ]);

  readonly selectedSector = signal<Sector>(this.sectors()[0]);
  readonly customAddress = signal<string>('');
  readonly isLoading = signal<boolean>(false);

  ngOnInit(): void {
    this.cargarCoberturaReal();
  }

  cargarCoberturaReal(): void {
    this.isLoading.set(true);
    this.pedidoSvc.obtenerCobertura().subscribe({
      next: (comunas: CoberturaComuna[]) => {
        this.isLoading.set(false);
        if (comunas && comunas.length > 0) {
          const mappedSectors: Sector[] = comunas
            .filter((c) => c.activa)
            .map((c, index) => {
              const key = c.nombre.toLowerCase().trim();
              const coords = PIN_COORDINATES[key] || {
                x: 35 + ((index * 13) % 45),
                y: 35 + ((index * 17) % 45)
              };
              return {
                id: key.replace(/\s+/g, '-'),
                name: c.nombre,
                eta: `${Math.max(c.tiempoEstimadoMinutos - 5, 15)}-${c.tiempoEstimadoMinutos + 10} min`,
                active: c.activa,
                pinX: coords.x,
                pinY: coords.y
              };
            });

          if (mappedSectors.length > 0) {
            this.sectors.set(mappedSectors);
            this.selectedSector.set(mappedSectors[0]);
          }
        }
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  onSelectSector(sec: Sector): void {
    this.selectedSector.set(sec);
    this.customAddress.set(`${sec.name}, Santiago`);
  }

  confirm(): void {
    const loc = this.customAddress().trim() || `${this.selectedSector().name}, Santiago`;
    this.selectLocation.emit(loc);
    this.close.emit();
  }
}
