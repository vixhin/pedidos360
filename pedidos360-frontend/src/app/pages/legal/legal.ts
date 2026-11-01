import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';

@Component({
  selector: 'app-legal',
  standalone: true,
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './legal.html',
  styleUrl: './legal.css',
})
export class Legal {
  readonly activeSection = signal<string>('terminos');

  setSection(section: string): void {
    this.activeSection.set(section);
  }
}

