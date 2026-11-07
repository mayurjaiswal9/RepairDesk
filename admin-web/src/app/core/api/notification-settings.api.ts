import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface NotificationRule {
  eventKey: string;
  label: string;
  description: string;
  enabled: boolean;
  messageTemplate: string;
  notifyCustomer: boolean;
  notifyTechnician: boolean;
  notifyStaff: boolean;
  notifyAdmin: boolean;
  extraUserIds: string[];
  channelInApp: boolean;
  channelPhone: boolean;
  channelDesktop: boolean;
  channelWhatsapp: boolean;
  channelSms: boolean;
}

export interface NotificationPerson {
  id: string;
  name: string;
  role: string;
}

export interface NotificationSettings {
  rules: NotificationRule[];
  people: NotificationPerson[];
  phoneDeliveryConfigured: boolean;
  desktopPushConfigured: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationSettingsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  get(): Observable<NotificationSettings> {
    return this.http.get<NotificationSettings>(`${this.base}/settings/notifications`);
  }

  save(rules: NotificationRule[]): Observable<NotificationSettings> {
    const body = {
      rules: rules.map((rule) => ({
        ...rule,
        channelWhatsapp: false,
        channelSms: false,
      })),
    };
    return this.http.put<NotificationSettings>(`${this.base}/settings/notifications`, body);
  }
}
