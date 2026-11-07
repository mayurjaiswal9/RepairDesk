import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import {
  NotificationPerson,
  NotificationRule,
  NotificationSettingsApi,
} from '../../core/api/notification-settings.api';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

@Component({
  selector: 'app-notification-settings',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSnackBarModule,
  ],
  templateUrl: './notification-settings.component.html',
  styleUrl: './notification-settings.component.scss',
})
export class NotificationSettingsComponent implements OnInit {
  private readonly api = inject(NotificationSettingsApi);
  private readonly snack = inject(MatSnackBar);

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly saving = signal(false);
  readonly rules = signal<NotificationRule[]>([]);
  readonly people = signal<NotificationPerson[]>([]);
  readonly phoneReady = signal(false);
  readonly placeholders = 'Use {reference}, {date}, {time}, {reason}, {message}, or {when}.';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.api.get().subscribe({
      next: (settings) => {
        this.rules.set(settings.rules.map((rule) => ({ ...rule, extraUserIds: [...rule.extraUserIds] })));
        this.people.set(settings.people);
        this.phoneReady.set(settings.phoneDeliveryConfigured);
        this.state.set('ready');
      },
      error: (err) => {
        const mapped = mapHttpError(err, "Couldn't load notification settings");
        this.error.set(mapped);
        this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
      },
    });
  }

  save(): void {
    this.saving.set(true);
    this.api.save(this.rules()).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (settings) => {
        this.rules.set(settings.rules.map((rule) => ({ ...rule, extraUserIds: [...rule.extraUserIds] })));
        this.snack.open('Notification settings saved', undefined, { duration: 3000 });
      },
      error: (err) => {
        const mapped = mapHttpError(err, "Couldn't save notification settings");
        this.snack.open(mapped.message, undefined, { duration: 5000 });
      },
    });
  }

  personName(id: string): string {
    return this.people().find((person) => person.id === id)?.name ?? 'Person';
  }
}
