import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { JobsApi } from '../../../core/api/jobs.api';
import { mapHttpError } from '../../../core/api/api-error';
import { UserProfile } from '../../../core/models/dashboard.models';
import { JobDetail, Priority } from '../../../core/models/job.models';

export type JobDialogResult = boolean | 'reload' | undefined;

interface BaseData {
  job: JobDetail;
}

@Component({
  selector: 'app-job-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p class="body">{{ data.body }}</p>
      @if (error) {
        <div class="err" role="alert">
          {{ error }}
          @if (stale) {
            <button type="button" class="link" (click)="reload()">Reload</button>
          }
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        [color]="data.danger ? 'warn' : 'primary'"
        type="button"
        [disabled]="busy"
        (click)="confirm()"
      >
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 8px; color: var(--color-text-secondary); }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
    .link {
      margin-left: 8px; border: none; background: none;
      color: var(--color-primary); font-weight: 600; cursor: pointer;
    }
  `,
})
export class JobConfirmDialogComponent {
  readonly data = inject<{
    job: JobDetail;
    title: string;
    body: string;
    confirmLabel: string;
    danger?: boolean;
    action: 'start' | 'resume' | 'unassign';
  }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<JobConfirmDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);

  busy = false;
  error: string | null = null;
  stale = false;

  confirm(): void {
    this.busy = true;
    this.error = null;
    this.stale = false;
    const id = this.data.job.id;
    const call =
      this.data.action === 'start'
        ? this.api.start(id)
        : this.data.action === 'resume'
          ? this.api.resume(id)
          : this.api.unassign(id);
    call.pipe(finalize(() => (this.busy = false))).subscribe({
      next: () => this.ref.close(true),
      error: (err) => this.onError(err),
    });
  }

  reload(): void {
    this.ref.close('reload');
  }

  private onError(err: unknown): void {
    const mapped = mapHttpError(err);
    if (mapped.status === 409) {
      this.stale = true;
      const status = this.data.job.status.replaceAll('_', ' ').toLowerCase();
      this.error = `This job changed since you opened it — its status may no longer be ${status}.`;
    } else {
      this.error = mapped.message;
    }
  }
}

@Component({
  selector: 'app-complete-job-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Mark {{ data.job.referenceNumber }} complete?</h2>
    <mat-dialog-content>
      <p class="body">
        The customer will be notified. Completion is final — the job cannot be reopened.
      </p>
      @if (error) {
        <div class="err" role="alert">
          {{ error }}
          @if (stale) {
            <button type="button" class="link" (click)="ref.close('reload')">Reload</button>
          }
        </div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Resolution summary</mat-label>
        <textarea matInput rows="5" [formControl]="summary" required></textarea>
        @if (summary.touched && summary.hasError('required')) {
          <mat-error>Describe what was done</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Mark complete
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
    .link {
      margin-left: 8px; border: none; background: none;
      color: var(--color-primary); font-weight: 600; cursor: pointer;
    }
  `,
})
export class CompleteJobDialogComponent {
  readonly data = inject<BaseData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<CompleteJobDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);
  private readonly fb = inject(FormBuilder);

  readonly summary = this.fb.nonNullable.control('', Validators.required);
  busy = false;
  error: string | null = null;
  stale = false;

  confirm(): void {
    this.summary.markAsTouched();
    if (this.summary.invalid) return;
    this.busy = true;
    this.error = null;
    this.api
      .complete(this.data.job.id, this.summary.value.trim())
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 409) {
            this.stale = true;
            this.error = 'This job changed since you opened it.';
          } else if (mapped.fieldErrors['resolutionSummary']) {
            this.summary.setErrors({ server: mapped.fieldErrors['resolutionSummary'] });
            this.error = mapped.fieldErrors['resolutionSummary'];
          } else {
            this.error = mapped.message;
          }
        },
      });
  }
}

@Component({
  selector: 'app-cancel-job-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Cancel {{ data.job.referenceNumber }}?</h2>
    <mat-dialog-content>
      <p class="body">
        Any active appointment is cancelled with the job, and the customer is notified once.
      </p>
      @if (error) {
        <div class="err" role="alert">
          {{ error }}
          @if (stale) {
            <button type="button" class="link" (click)="ref.close('reload')">Reload</button>
          }
        </div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Reason</mat-label>
        <textarea matInput rows="4" [formControl]="reason" required></textarea>
        @if (reason.touched && reason.hasError('required')) {
          <mat-error>A cancellation reason is required</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Keep job</button>
      <button mat-flat-button color="warn" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Cancel job
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
    .link {
      margin-left: 8px; border: none; background: none;
      color: var(--color-primary); font-weight: 600; cursor: pointer;
    }
  `,
})
export class CancelJobDialogComponent {
  readonly data = inject<BaseData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<CancelJobDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);
  private readonly fb = inject(FormBuilder);

  readonly reason = this.fb.nonNullable.control('', Validators.required);
  busy = false;
  error: string | null = null;
  stale = false;

  confirm(): void {
    this.reason.markAsTouched();
    if (this.reason.invalid) return;
    this.busy = true;
    this.error = null;
    this.api
      .cancel(this.data.job.id, this.reason.value.trim())
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 409) {
            this.stale = true;
            this.error = 'This job changed since you opened it.';
          } else {
            this.error = mapped.message;
          }
        },
      });
  }
}

@Component({
  selector: 'app-awaiting-parts-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Mark {{ data.job.referenceNumber }} awaiting parts?</h2>
    <mat-dialog-content>
      <p class="body">Optionally note which parts are needed (saved as an internal note).</p>
      @if (error) {
        <div class="err" role="alert">
          {{ error }}
          @if (stale) {
            <button type="button" class="link" (click)="ref.close('reload')">Reload</button>
          }
        </div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Parts needed</mat-label>
        <textarea matInput rows="3" [formControl]="note" placeholder="Optional…"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Mark awaiting parts
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
    .link {
      margin-left: 8px; border: none; background: none;
      color: var(--color-primary); font-weight: 600; cursor: pointer;
    }
  `,
})
export class AwaitingPartsDialogComponent {
  readonly data = inject<BaseData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<AwaitingPartsDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);
  private readonly fb = inject(FormBuilder);

  readonly note = this.fb.nonNullable.control('');
  busy = false;
  error: string | null = null;
  stale = false;

  confirm(): void {
    this.busy = true;
    this.error = null;
    const id = this.data.job.id;
    const note = this.note.value.trim();
    this.api
      .awaitingParts(id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => {
          if (!note) {
            this.ref.close(true);
            return;
          }
          this.api.appendNote(id, `Parts needed: ${note}`).subscribe({
            next: () => this.ref.close(true),
            error: () => this.ref.close(true), // status already changed
          });
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 409) {
            this.stale = true;
            this.error = 'This job changed since you opened it.';
          } else {
            this.error = mapped.message;
          }
        },
      });
  }
}

@Component({
  selector: 'app-assign-tech-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ data.job.assignedTechnicianId ? 'Change technician' : 'Assign technician' }}
    </h2>
    <mat-dialog-content>
      @if (error) {
        <div class="err" role="alert">
          {{ error }}
          @if (stale) {
            <button type="button" class="link" (click)="ref.close('reload')">Reload</button>
          }
        </div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Technician</mat-label>
        <mat-select [formControl]="techId">
          @for (t of data.technicians; track t.id) {
            <mat-option [value]="t.id">
              {{ t.name || t.username }}
              <span class="secondary">{{ '@' + t.username }}</span>
            </mat-option>
          }
        </mat-select>
        @if (techId.touched && techId.hasError('required')) {
          <mat-error>Select a technician</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        {{ data.job.assignedTechnicianId ? 'Change' : 'Assign' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; }
    .secondary { color: var(--color-text-secondary); font-size: 12px; margin-left: 6px; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
    .link {
      margin-left: 8px; border: none; background: none;
      color: var(--color-primary); font-weight: 600; cursor: pointer;
    }
  `,
})
export class AssignTechDialogComponent {
  readonly data = inject<BaseData & { technicians: UserProfile[] }>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<AssignTechDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);
  private readonly fb = inject(FormBuilder);

  readonly techId = this.fb.nonNullable.control(
    this.data.job.assignedTechnicianId ?? '',
    Validators.required,
  );
  busy = false;
  error: string | null = null;
  stale = false;

  confirm(): void {
    this.techId.markAsTouched();
    if (this.techId.invalid) return;
    this.busy = true;
    this.error = null;
    this.api
      .assign(this.data.job.id, this.techId.value)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 409) {
            this.stale = true;
            this.error = 'Active technician not found or job not assignable.';
          } else {
            this.error = mapped.message;
          }
        },
      });
  }
}

@Component({
  selector: 'app-priority-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Set priority</h2>
    <mat-dialog-content>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Priority</mat-label>
        <mat-select [formControl]="priority">
          <mat-option value="LOW">Low</mat-option>
          <mat-option value="NORMAL">Normal</mat-option>
          <mat-option value="HIGH">High</mat-option>
          <mat-option value="URGENT">Urgent</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Save
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class PriorityDialogComponent {
  readonly data = inject<BaseData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PriorityDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);
  private readonly fb = inject(FormBuilder);

  readonly priority = this.fb.nonNullable.control<Priority>(this.data.job.priority);
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.busy = true;
    this.api
      .setPriority(this.data.job.id, this.priority.value)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => (this.error = mapHttpError(err).message),
      });
  }
}

@Component({
  selector: 'app-warranty-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Confirm warranty</h2>
    <mat-dialog-content>
      <p class="body">Staff confirmation — separate from the customer's original claim.</p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Warranty confirmed by staff</mat-label>
        <mat-select [formControl]="confirmed">
          <mat-option [value]="true">Yes</mat-option>
          <mat-option [value]="false">No</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Save
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class WarrantyDialogComponent {
  readonly data = inject<BaseData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<WarrantyDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);
  private readonly fb = inject(FormBuilder);

  readonly confirmed = this.fb.nonNullable.control(this.data.job.warrantyConfirmed);
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.busy = true;
    this.api
      .setWarranty(this.data.job.id, this.confirmed.value)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => (this.error = mapHttpError(err).message),
      });
  }
}

@Component({
  selector: 'app-add-note-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Add note</h2>
    <mat-dialog-content>
      <p class="body">Notes are internal, permanent, and attributed to you. Customers never see them.</p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Note</mat-label>
        <textarea matInput rows="5" [formControl]="note" required></textarea>
        @if (note.touched && note.hasError('required')) {
          <mat-error>Enter a note</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Add note
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class AddNoteDialogComponent {
  readonly data = inject<BaseData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<AddNoteDialogComponent, JobDialogResult>);
  private readonly api = inject(JobsApi);
  private readonly fb = inject(FormBuilder);

  readonly note = this.fb.nonNullable.control('', Validators.required);
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.note.markAsTouched();
    if (this.note.invalid) return;
    this.busy = true;
    this.api
      .appendNote(this.data.job.id, this.note.value.trim())
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => (this.error = mapHttpError(err).message),
      });
  }
}
