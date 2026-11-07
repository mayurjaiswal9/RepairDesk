import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotificationsApi, AppNotification } from '../api/notifications.api';

const DISMISS_KEY = 'repairdesk.desktopAlerts.dismissed';

interface PushPayload {
  title?: string;
  body?: string;
  url?: string;
  id?: string;
}

@Injectable({ providedIn: 'root' })
export class DesktopAlertService {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(NotificationsApi);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly shown = new Set<string>();
  private listening = false;

  readonly ask = signal(false);

  start(): void {
    if (this.listening || typeof window === 'undefined' || !('Notification' in window)) {
      return;
    }
    this.listening = true;
    this.ask.set(this.shouldAsk());
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.addEventListener('message', (event: MessageEvent) => {
        const data = event.data as { kind?: string; payload?: PushPayload; url?: string } | null;
        if (!data) return;
        if (data.kind === 'repairdesk-push' && data.payload) {
          this.present(data.payload.id ?? '', data.payload.body ?? '', data.payload.url ?? '/notifications');
        }
        if (data.kind === 'repairdesk-open' && data.url) {
          void this.router.navigateByUrl(data.url);
        }
      });
      if (Notification.permission === 'granted') {
        void this.subscribe();
      }
    }
  }

  async enable(): Promise<void> {
    if (!('Notification' in window)) return;
    const permission = await Notification.requestPermission();
    this.ask.set(this.shouldAsk());
    if (permission === 'granted') {
      await this.subscribe();
    }
  }

  dismiss(): void {
    localStorage.setItem(DISMISS_KEY, '1');
    this.ask.set(false);
  }

  revealLatest(): void {
    this.notifications.list().subscribe({
      next: (items) => {
        const newest = items.find((item) => !item.read);
        if (!newest) return;
        this.present(newest.id, newest.message, linkFor(newest));
      },
      error: () => undefined,
    });
  }

  async stop(): Promise<void> {
    if (!('serviceWorker' in navigator)) return;
    try {
      const registration = await navigator.serviceWorker.getRegistration();
      const subscription = await registration?.pushManager.getSubscription();
      if (!subscription) return;
      await firstValueFrom(
        this.http.delete<void>(`${environment.apiBaseUrl}/me/push-subscriptions`, {
          body: { endpoint: subscription.endpoint, p256dh: '-', auth: '-' },
        }),
      ).catch(() => undefined);
      await subscription.unsubscribe();
    } catch {
      // Signing out still continues when the browser refuses the unsubscribe.
    }
  }

  private shouldAsk(): boolean {
    if (!('Notification' in window)) return false;
    if (Notification.permission !== 'default') return false;
    return localStorage.getItem(DISMISS_KEY) !== '1';
  }

  private present(id: string, body: string, url: string): void {
    if (!body || (id && this.shown.has(id))) return;
    if (id) {
      this.shown.add(id);
      if (this.shown.size > 50) this.shown.delete(this.shown.values().next().value as string);
    }
    const hidden = typeof document !== 'undefined' && document.hidden;
    if (hidden && 'Notification' in window && Notification.permission === 'granted') {
      const notice = new Notification('RepairDesk', { body, tag: id || undefined });
      notice.onclick = () => {
        window.focus();
        void this.router.navigateByUrl(url);
        notice.close();
      };
      return;
    }
    const ref = this.snack.open(body, 'View', { duration: 6000 });
    ref.onAction().subscribe(() => void this.router.navigateByUrl(url));
  }

  private async subscribe(): Promise<void> {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;
    try {
      await this.registerPush();
    } catch {
      // The inbox still works when this browser has no push service.
    }
  }

  private async registerPush(): Promise<void> {
    const key = await firstValueFrom(
      this.http.get<{ publicKey: string; configured: boolean }>(
        `${environment.apiBaseUrl}/me/push-subscriptions/key`,
      ),
    ).catch(() => null);
    if (!key?.configured || !key.publicKey) return;
    const registration = await navigator.serviceWorker.register('/push-sw.js');
    const existing = await registration.pushManager.getSubscription();
    const subscription = existing ?? await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(key.publicKey),
    });
    const json = subscription.toJSON();
    await firstValueFrom(
      this.http.post<void>(`${environment.apiBaseUrl}/me/push-subscriptions`, {
        endpoint: subscription.endpoint,
        p256dh: json.keys?.['p256dh'] ?? '',
        auth: json.keys?.['auth'] ?? '',
      }),
    );
  }
}

function linkFor(item: AppNotification): string {
  if (!item.relatedEntityId) return '/notifications';
  if (item.relatedType === 'REPAIR_REQUEST') return `/requests/${item.relatedEntityId}`;
  if (item.relatedType === 'JOB' || item.relatedType === 'APPOINTMENT') {
    return `/jobs/${item.relatedEntityId}`;
  }
  return '/notifications';
}

function urlBase64ToUint8Array(value: string): Uint8Array {
  const padding = '='.repeat((4 - (value.length % 4)) % 4);
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) output[i] = raw.charCodeAt(i);
  return output;
}
