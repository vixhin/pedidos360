import { ChangeDetectionStrategy, Component, input, numberAttribute } from '@angular/core';

export type IconName =
  | 'search' | 'map-pin' | 'heart' | 'bell' | 'cart' | 'chevron-down'
  | 'chevron-right' | 'user' | 'x' | 'star' | 'check' | 'truck' | 'shield'
  | 'gift' | 'settings' | 'package' | 'credit-card' | 'help-circle' | 'lock'
  | 'edit' | 'plus' | 'minus' | 'clock' | 'arrow-right' | 'trash' | 'logout'
  | 'home' | 'list' | 'bolt';

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth()"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (name()) {
        @case ('search') {
          <circle cx="11" cy="11" r="7" />
          <line x1="16.5" y1="16.5" x2="21" y2="21" />
        }
        @case ('map-pin') {
          <path d="M12 21s-7-7.1-7-12a7 7 0 0 1 14 0c0 4.9-7 12-7 12z" />
          <circle cx="12" cy="9" r="2.5" />
        }
        @case ('heart') {
          <path d="M12 20.5s-7.5-5-7.5-10A4.5 4.5 0 0 1 12 6a4.5 4.5 0 0 1 7.5 4.5c0 5-7.5 10-7.5 10z" />
        }
        @case ('bell') {
          <path d="M6 9a6 6 0 0 1 12 0c0 4 1.5 5.5 1.5 5.5h-15S6 13 6 9z" />
          <path d="M9.5 17a2.5 2.5 0 0 0 5 0" />
        }
        @case ('cart') {
          <circle cx="9" cy="20" r="1.4" />
          <circle cx="18" cy="20" r="1.4" />
          <path d="M2.5 3h2l2.4 12.2a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.6L21 7H6" />
        }
        @case ('chevron-down') {
          <path d="M6 9l6 6 6-6" />
        }
        @case ('chevron-right') {
          <path d="M9 6l6 6-6 6" />
        }
        @case ('user') {
          <circle cx="12" cy="8" r="4" />
          <path d="M4 20c0-4 4-6 8-6s8 2 8 6" />
        }
        @case ('x') {
          <line x1="6" y1="6" x2="18" y2="18" />
          <line x1="18" y1="6" x2="6" y2="18" />
        }
        @case ('star') {
          <path d="M12 2.5l2.9 6 6.6.6-5 4.4 1.5 6.4L12 16.7 6 19.9l1.5-6.4-5-4.4 6.6-.6z" />
        }
        @case ('check') {
          <path d="M4 12l5 5 11-11" />
        }
        @case ('truck') {
          <rect x="1" y="6.5" width="13" height="9.5" rx="1" />
          <path d="M14 9.5h4l3.5 3v3.5h-7.5z" />
          <circle cx="6.5" cy="18.5" r="1.7" />
          <circle cx="17.5" cy="18.5" r="1.7" />
        }
        @case ('shield') {
          <path d="M12 2.5l7 3v5.5c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V5.5l7-3z" />
          <path d="M9 12l2 2 4-4" />
        }
        @case ('gift') {
          <rect x="3" y="9" width="18" height="12" rx="1" />
          <line x1="12" y1="9" x2="12" y2="21" />
          <path d="M3 9h18v4H3z" />
          <path d="M12 9c-1.6-3.2-5.3-3.2-5.3 0S10.4 10.2 12 9z" />
          <path d="M12 9c1.6-3.2 5.3-3.2 5.3 0S13.6 10.2 12 9z" />
        }
        @case ('settings') {
          <circle cx="12" cy="12" r="3.2" />
          <circle cx="12" cy="12" r="8" stroke-dasharray="2.2 3.4" />
        }
        @case ('package') {
          <path d="M21 8l-9-5-9 5 9 5 9-5z" />
          <path d="M3 8v8l9 5 9-5V8" />
          <line x1="12" y1="13" x2="12" y2="21" />
        }
        @case ('credit-card') {
          <rect x="2" y="5" width="20" height="14" rx="2" />
          <line x1="2" y1="10" x2="22" y2="10" />
        }
        @case ('help-circle') {
          <circle cx="12" cy="12" r="9" />
          <path d="M9.5 9.3a2.6 2.6 0 1 1 3.6 2.4c-.9.4-1.1 1-1.1 2" />
          <path d="M12 17h.01" />
        }
        @case ('lock') {
          <rect x="5" y="11" width="14" height="9" rx="2" />
          <path d="M8 11V8a4 4 0 0 1 8 0v3" />
        }
        @case ('edit') {
          <path d="M12 20h9" />
          <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" />
        }
        @case ('plus') {
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        }
        @case ('minus') {
          <line x1="5" y1="12" x2="19" y2="12" />
        }
        @case ('clock') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3.5 2" />
        }
        @case ('arrow-right') {
          <line x1="5" y1="12" x2="19" y2="12" />
          <path d="M13 6l6 6-6 6" />
        }
        @case ('trash') {
          <path d="M4 7h16" />
          <path d="M10 11v6M14 11v6" />
          <path d="M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13" />
          <path d="M9 7V4h6v3" />
        }
        @case ('logout') {
          <path d="M9 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4" />
          <path d="M15 16l5-4-5-4" />
          <line x1="20" y1="12" x2="9" y2="12" />
        }
        @case ('home') {
          <path d="M4 11l8-7 8 7" />
          <path d="M6 10v9a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-9" />
        }
        @case ('list') {
          <line x1="8" y1="7" x2="21" y2="7" />
          <line x1="8" y1="12" x2="21" y2="12" />
          <line x1="8" y1="17" x2="21" y2="17" />
          <line x1="3" y1="7" x2="3.01" y2="7" />
          <line x1="3" y1="12" x2="3.01" y2="12" />
          <line x1="3" y1="17" x2="3.01" y2="17" />
        }
        @case ('bolt') {
          <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8z" />
        }
      }
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(20, { transform: numberAttribute });
  readonly strokeWidth = input(1.8, { transform: numberAttribute });
}
