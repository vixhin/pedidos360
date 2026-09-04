import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Icon } from '../../shared/icon/icon';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './footer.html',
  styleUrl: './footer.css',
})
export class Footer {
  readonly year = new Date().getFullYear();
}
