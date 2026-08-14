import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-simple-placeholder',
  standalone: true,
  template: `
    <h1 class="display" tabindex="-1">{{ title }}</h1>
    <p class="meta">{{ blurb }}</p>
  `,
  styles: `
    .display { margin: 0; font-size: 28px; font-weight: 700; letter-spacing: -0.03em; }
    .meta { margin: 8px 0 0; color: var(--color-text-secondary); max-width: 40rem; }
  `,
})
export class SimplePlaceholderComponent {
  private readonly route = inject(ActivatedRoute);
  readonly title = (this.route.snapshot.data['title'] as string) || 'Page';
  readonly blurb =
    (this.route.snapshot.data['blurb'] as string) ||
    'This page will be implemented in a later batch.';
}
