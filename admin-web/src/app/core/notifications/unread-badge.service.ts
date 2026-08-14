import { Injectable, signal } from '@angular/core';

/** Shared unread count for the shell bell and the notifications inbox. */
@Injectable({ providedIn: 'root' })
export class UnreadBadgeService {
  private readonly countSignal = signal(0);
  readonly count = this.countSignal.asReadonly();

  set(count: number): void {
    this.countSignal.set(Math.max(0, count));
  }

  decrement(by = 1): void {
    this.countSignal.update((c) => Math.max(0, c - by));
  }

  zero(): void {
    this.countSignal.set(0);
  }
}
