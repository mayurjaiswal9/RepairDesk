import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="panel">
      <h1 class="display" tabindex="-1">You don't have access</h1>
      <p>This page isn't available for your role.</p>
      <a routerLink="/dashboard">Back to dashboard</a>
    </div>
  `,
  styles: `
    .panel {
      max-width: 480px; padding: 32px; background: var(--color-surface);
      border: 1px solid var(--color-divider); border-radius: var(--radius-m);
      box-shadow: var(--shadow-card);
    }
    .display { margin: 0 0 8px; font-size: 24px; font-weight: 700; }
    p { color: var(--color-text-secondary); }
    a { color: var(--color-primary); font-weight: 600; }
  `,
})
export class ForbiddenComponent {}
