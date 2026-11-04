import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AttendanceApi, AttendanceRow } from '../../core/api/attendance.api';
import { UsersApi } from '../../core/api/users.api';
import { UserProfile } from '../../core/models/dashboard.models';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

const FLAG_LABELS: Record<string, string> = {
  INSIDE: 'Inside shop',
  OUTSIDE: 'Outside shop',
  UNCERTAIN: 'Location uncertain',
  MISSING: 'Location missing',
  MOCKED: 'Mocked location',
};

@Component({
  selector: 'app-attendance-list',
  standalone: true,
  imports: [RouterLink, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './attendance-list.component.html',
  styleUrl: './attendance-list.component.scss',
})
export class AttendanceListComponent implements OnInit {
  private readonly api = inject(AttendanceApi);
  private readonly users = inject(UsersApi);

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly rows = signal<AttendanceRow[]>([]);
  readonly technicians = signal<UserProfile[]>([]);
  readonly attendanceEnabled = signal(true);
  readonly from = signal(indiaToday());
  readonly to = signal(indiaToday());
  readonly technicianId = signal('');
  readonly rangeError = signal<string | null>(null);

  ngOnInit(): void {
    this.api.config().subscribe({
      next: (config) => this.attendanceEnabled.set(config.attendanceEnabled),
      error: () => this.attendanceEnabled.set(true),
    });
    this.users.listByRole('TECHNICIAN').subscribe({
      next: (technicians) => this.technicians.set(technicians),
      error: () => this.technicians.set([]),
    });
    this.load();
  }

  load(): void {
    const problem = rangeProblem(this.from(), this.to());
    this.rangeError.set(problem);
    if (problem) {
      this.state.set('ready');
      this.rows.set([]);
      return;
    }
    this.state.set('loading');
    this.api.list(this.from(), this.to(), this.technicianId() || null).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.state.set('ready');
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        this.error.set(mapped);
        this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
      },
    });
  }

  onFromInput(event: Event): void {
    this.from.set((event.target as HTMLInputElement).value);
  }

  onToInput(event: Event): void {
    this.to.set((event.target as HTMLInputElement).value);
  }

  onTechnicianInput(event: Event): void {
    this.technicianId.set((event.target as HTMLSelectElement).value);
  }

  technicianLabel(tech: UserProfile): string {
    return tech.name?.trim() || tech.username;
  }

  download(): void {
    const problem = rangeProblem(this.from(), this.to());
    this.rangeError.set(problem);
    if (problem) return;
    this.api.export(this.from(), this.to(), this.technicianId() || null).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'attendance.csv';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        const mapped = mapHttpError(err, 'Could not download the attendance file.');
        this.error.set(mapped);
        this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
      },
    });
  }

  statusLabel(row: AttendanceRow): string {
    switch (row.status) {
      case 'ON_DUTY':
        return `On duty since ${this.clock(row.startAt)}`;
      case 'ENDED':
        return 'Ended';
      case 'AUTO_ENDED':
        return 'Auto-ended';
      default:
        return 'Not started';
    }
  }

  flagLabel(flag?: string | null): string {
    if (!flag) return '';
    return FLAG_LABELS[flag] ?? flag;
  }

  reasonLabel(reason?: string | null): string {
    if (reason === 'MANUAL') return 'Manual';
    if (reason === 'AUTO') return 'Auto';
    return '';
  }

  hoursLabel(minutes?: number | null): string {
    if (minutes == null || minutes < 0) return '';
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return `${hours}h ${rest.toString().padStart(2, '0')}m`;
  }

  workDate(iso: string): string {
    const [year, month, day] = iso.split('-');
    return `${day}-${month}-${year}`;
  }

  clock(iso?: string | null): string {
    if (!iso) return '';
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Kolkata',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(new Date(iso));
  }
}

function indiaToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
}

function rangeProblem(from: string, to: string): string | null {
  if (!from || !to) return 'Choose a from date and a to date.';
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(start) || Number.isNaN(end)) return 'Choose a from date and a to date.';
  const days = Math.round((end - start) / 86_400_000);
  if (days < 0) return 'From date must be on or before the to date.';
  if (days > 30) return 'Date range must be 31 days or less.';
  return null;
}
