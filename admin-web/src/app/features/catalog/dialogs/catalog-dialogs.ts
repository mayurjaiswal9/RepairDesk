import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { CategoriesApi } from '../../../core/api/categories.api';
import { mapHttpError } from '../../../core/api/api-error';
import { DeviceBrand, DeviceCategory } from '../../../core/models/request.models';

export type CatalogDialogResult =
  | boolean
  | 'stale'
  | 'add_another'
  | DeviceCategory
  | DeviceBrand
  | undefined;

@Component({
  selector: 'app-category-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.category ? 'Edit category' : 'Add category' }}</h2>
    <mat-dialog-content>
      @if (stale) {
        <div class="err" role="alert">
          This record changed since you opened it.
          <button mat-button type="button" color="primary" (click)="ref.close('stale')">Reload</button>
        </div>
      } @else if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="100" />
          @if (form.controls.name.touched && form.controls.name.hasError('required')) {
            <mat-error>Name is required</mat-error>
          } @else if (form.controls.name.hasError('server')) {
            <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Description</mat-label>
          <textarea matInput rows="2" formControlName="description" maxlength="500"></textarea>
          <mat-hint align="end">{{ form.controls.description.value.length || 0 }}/500</mat-hint>
        </mat-form-field>
        <fieldset class="radios">
          <legend>Brand mode</legend>
          <mat-radio-group formControlName="brandMode">
            <mat-radio-button value="FREE_TEXT">
              Free text
              <span class="hint">Customers pick 'Others' or type their own brand.</span>
            </mat-radio-button>
            <mat-radio-button value="CONFIGURED">
              Configured list
              <span class="hint">Customers pick from brands you curate, plus 'Others'.</span>
            </mat-radio-button>
          </mat-radio-group>
          @if (form.controls.brandMode.value === 'CONFIGURED') {
            <p class="note">You'll need to add at least one brand for customers to choose from.</p>
          }
        </fieldset>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="busy || form.invalid || form.pristine"
        (click)="confirm()"
      >
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Save
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .form { display: flex; flex-direction: column; }
    .full { width: 100%; }
    .radios {
      border: none; margin: 0 0 8px; padding: 0;
      legend { font-size: 13px; font-weight: 700; margin-bottom: 8px; }
      mat-radio-button { display: block; margin-bottom: 10px; }
      .hint { display: block; margin-left: 28px; font-size: 12px; color: var(--color-text-secondary); font-weight: 400; }
    }
    .note { margin: 0; font-size: 12px; color: var(--color-text-secondary); }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
      display: flex; flex-wrap: wrap; align-items: center; gap: 8px;
    }
  `,
})
export class CategoryFormDialogComponent {
  readonly data = inject<{ category?: DeviceCategory }>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<CategoryFormDialogComponent, CatalogDialogResult>);
  private readonly api = inject(CategoriesApi);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    name: [this.data.category?.name ?? '', [Validators.required, Validators.maxLength(100)]],
    description: [this.data.category?.description ?? '', Validators.maxLength(500)],
    brandMode: [
      (this.data.category?.brandMode as string) || 'FREE_TEXT',
      Validators.required,
    ],
  });
  busy = false;
  error: string | null = null;
  stale = false;

  confirm(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.stale) return;
    this.busy = true;
    this.error = null;
    const body = {
      name: this.form.controls.name.value.trim(),
      description: this.form.controls.description.value.trim() || null,
      brandMode: this.form.controls.brandMode.value,
    };
    const req = this.data.category
      ? this.api.update(this.data.category.id, body)
      : this.api.create(body);
    req.pipe(finalize(() => (this.busy = false))).subscribe({
      next: (saved) => this.ref.close(saved),
      error: (err) => {
        const mapped = mapHttpError(err);
        if (mapped.status === 409 && /changed|version|conflict/i.test(mapped.message)
            && !/already exists/i.test(mapped.message)) {
          this.stale = true;
          return;
        }
        if (mapped.status === 409 || mapped.fieldErrors['name']) {
          this.form.controls.name.setErrors({
            server:
              mapped.fieldErrors['name'] ||
              `A category named '${body.name}' already exists.`,
          });
          return;
        }
        this.error = mapped.message;
      },
    });
  }
}

@Component({
  selector: 'app-archive-category-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Archive '{{ data.category.name }}'?</h2>
    <mat-dialog-content>
      <p class="body">
        Customers will no longer be able to choose this category for new requests.
        <strong>Existing requests and jobs keep it</strong> and are not affected. You can restore it
        at any time.
      </p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Archive
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0; color: var(--color-text-secondary); line-height: 1.5; }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class ArchiveCategoryDialogComponent {
  readonly data = inject<{ category: DeviceCategory }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ArchiveCategoryDialogComponent, CatalogDialogResult>);
  private readonly api = inject(CategoriesApi);
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.busy = true;
    this.error = null;
    this.api
      .archive(this.data.category.id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => (this.error = mapHttpError(err).message),
      });
  }
}

@Component({
  selector: 'app-brand-form-dialog',
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
    <h2 mat-dialog-title>{{ data.brand ? 'Edit brand' : 'Add brand' }}</h2>
    <mat-dialog-content>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Brand name</mat-label>
        <input matInput [formControl]="name" maxlength="100" autofocus />
        @if (name.touched && name.hasError('required')) {
          <mat-error>Brand name is required</mat-error>
        } @else if (name.hasError('others')) {
          <mat-error>
            Customers are always offered Others automatically — you don't need to add it.
          </mat-error>
        } @else if (name.hasError('server')) {
          <mat-error>{{ name.getError('server') }}</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      @if (!data.brand) {
        <button
          mat-stroked-button
          color="primary"
          type="button"
          [disabled]="busy || name.invalid"
          (click)="confirm(true)"
        >
          Save and add another
        </button>
      }
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="busy || name.invalid || (!data.brand && name.pristine)"
        (click)="confirm(false)"
      >
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
export class BrandFormDialogComponent {
  readonly data = inject<{
    category: DeviceCategory;
    brand?: DeviceBrand;
    onCreated?: (brand: DeviceBrand) => void;
  }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<BrandFormDialogComponent, CatalogDialogResult>);
  private readonly api = inject(CategoriesApi);
  private readonly fb = inject(FormBuilder);

  readonly name = this.fb.nonNullable.control(this.data.brand?.name ?? '', [
    Validators.required,
    Validators.maxLength(100),
  ]);
  busy = false;
  error: string | null = null;

  confirm(addAnother: boolean): void {
    this.name.markAsTouched();
    const trimmed = this.name.value.trim();
    if (!trimmed) {
      this.name.setErrors({ required: true });
      return;
    }
    if (/^others?(\s*\/\s*custom)?$/i.test(trimmed)) {
      this.name.setErrors({ others: true });
      return;
    }
    this.busy = true;
    this.error = null;
    const req = this.data.brand
      ? this.api.updateBrand(this.data.category.id, this.data.brand.id, { name: trimmed })
      : this.api.createBrand(this.data.category.id, { name: trimmed });
    req.pipe(finalize(() => (this.busy = false))).subscribe({
      next: (saved) => {
        if (addAnother && !this.data.brand) {
          this.data.onCreated?.(saved);
          this.name.reset('');
          this.name.markAsPristine();
          this.name.markAsUntouched();
          return;
        }
        this.ref.close(saved);
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        if (mapped.status === 409) {
          this.name.setErrors({
            server: `${this.data.category.name} already has an active brand named '${trimmed}'.`,
          });
          return;
        }
        this.error = mapped.message;
      },
    });
  }
}

@Component({
  selector: 'app-archive-brand-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Archive '{{ data.brand.name }}'?</h2>
    <mat-dialog-content>
      <p class="body">
        Customers will no longer see {{ data.brand.name }} in the brand list for
        {{ data.category.name }}.
        <strong>Existing repair requests keep the brand they were submitted with</strong> and are not
        affected. You can restore it at any time.
      </p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Archive
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0; color: var(--color-text-secondary); line-height: 1.5; }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class ArchiveBrandDialogComponent {
  readonly data = inject<{ category: DeviceCategory; brand: DeviceBrand }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ArchiveBrandDialogComponent, CatalogDialogResult>);
  private readonly api = inject(CategoriesApi);
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.busy = true;
    this.error = null;
    this.api
      .archiveBrand(this.data.category.id, this.data.brand.id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => (this.error = mapHttpError(err).message),
      });
  }
}

@Component({
  selector: 'app-switch-brand-mode-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Switch {{ data.category.name }} to a configured brand list?</h2>
    <mat-dialog-content>
      <p class="body">
        Customers submitting a {{ data.category.name }} request will pick from your
        {{ data.activeCount }} active brand{{ data.activeCount === 1 ? '' : 's' }}, plus
        <strong>Others</strong>.
      </p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Switch
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0; color: var(--color-text-secondary); line-height: 1.5; }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class SwitchBrandModeDialogComponent {
  readonly data = inject<{ category: DeviceCategory; activeCount: number }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<SwitchBrandModeDialogComponent, CatalogDialogResult>);
  private readonly api = inject(CategoriesApi);
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.busy = true;
    this.error = null;
    const c = this.data.category;
    this.api
      .update(c.id, {
        name: c.name,
        description: c.description ?? null,
        brandMode: 'CONFIGURED',
      })
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (updated) => this.ref.close(updated),
        error: (err) => (this.error = mapHttpError(err).message),
      });
  }
}
