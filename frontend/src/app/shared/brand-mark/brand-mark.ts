import { Component, input } from '@angular/core';

/** The buy-02 logo glyph. `tone="light"` renders it for dark backgrounds. */
@Component({
  selector: 'app-brand-mark',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 32 32" aria-hidden="true">
      <rect
        width="32"
        height="32"
        rx="9"
        [attr.fill]="tone() === 'light' ? '#ffffff' : 'url(#bm)'"
      />
      <path
        d="M10 11.5h11.2l-1.4 7.6a2 2 0 0 1-2 1.6h-5.3a2 2 0 0 1-2-1.7L9 9.5H6.8"
        fill="none"
        [attr.stroke]="tone() === 'light' ? '#4b36ba' : '#ffffff'"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <circle cx="13" cy="24" r="1.6" [attr.fill]="tone() === 'light' ? '#4b36ba' : '#ffffff'" />
      <circle cx="19.4" cy="24" r="1.6" [attr.fill]="tone() === 'light' ? '#4b36ba' : '#ffffff'" />
      <defs>
        <linearGradient id="bm" x1="0" y1="0" x2="32" y2="32">
          <stop stop-color="#8e80f0" />
          <stop offset="1" stop-color="#4b36ba" />
        </linearGradient>
      </defs>
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
})
export class BrandMark {
  size = input(30);
  tone = input<'dark' | 'light'>('dark');
}
