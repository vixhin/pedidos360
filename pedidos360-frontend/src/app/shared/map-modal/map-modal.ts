import { ChangeDetectionStrategy, Component, EventEmitter, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Icon } from '../icon/icon';

export interface Sector {
  id: string;
  name: string;
  eta: string;
  active: boolean;
  pinX: number;
  pinY: number;
}

@Component({
  selector: 'app-map-modal',
  standalone: true,
  imports: [Icon, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './map-modal.html',
  styleUrl: './map-modal.css',
})
export class MapModal {
  @Output() close = new EventEmitter<void>();
  @Output() selectLocation = new EventEmitter<string>();

  readonly sectors: Sector[] = [
    { id: 'santiago-centro', name: 'Santiago Centro', eta: '25-40 min', active: true, pinX: 48, pinY: 46 },
    { id: 'providencia', name: 'Providencia', eta: '20-35 min', active: true, pinX: 58, pinY: 38 },
    { id: 'las-condes', name: 'Las Condes', eta: '25-45 min', active: true, pinX: 72, pinY: 30 },
    { id: 'nunoa', name: 'Ñuñoa', eta: '20-30 min', active: true, pinX: 62, pinY: 52 },
    { id: 'san-miguel', name: 'San Miguel', eta: '15-30 min', active: true, pinX: 46, pinY: 64 },
    { id: 'maipu', name: 'Maipú', eta: '30-50 min', active: true, pinX: 28, pinY: 62 },
  ];

  readonly selectedSector = signal<Sector>(this.sectors[0]);
  readonly customAddress = signal<string>('Av. Libertador Bernardo O\'Higgins 1449, Santiago');

  onSelectSector(sec: Sector): void {
    this.selectedSector.set(sec);
    this.customAddress.set(`${sec.name}, Santiago`);
  }

  confirm(): void {
    const loc = this.customAddress() || `${this.selectedSector().name}, Santiago`;
    this.selectLocation.emit(loc);
    this.close.emit();
  }
}
