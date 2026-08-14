import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { CategoriesApi } from '../../core/api/categories.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { DeviceCategory } from '../../core/models/request.models';
import {
  ArchiveCategoryDialogComponent,
  CategoryFormDialogComponent,
} from './dialogs/catalog-dialogs';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

@Component({
  selector: 'app-categories-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './categories-list.component.html',
  styleUrl: './categories-list.component.scss',
})
export class CategoriesListComponent implements OnInit {
  private readonly api = inject(CategoriesApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly all = signal<DeviceCategory[]>([]);
  readonly error = signal<MappedApiError | null>(null);
  readonly includeArchived = signal(false);
  readonly q = signal('');
  readonly page = signal(0);
  readonly size = signal(20);

  readonly searchCtrl = new FormControl('', { nonNullable: true });
  readonly archivedCtrl = new FormControl(false, { nonNullable: true });

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');

  readonly activeTotal = computed(() => this.all().filter((c) => !c.archived).length);
  readonly archivedTotal = computed(() => this.all().filter((c) => c.archived).length);

  readonly filtered = computed(() => {
    const q = this.q().toLowerCase();
    const showArchived = this.includeArchived();
    return this.all().filter((c) => {
      if (!showArchived && c.archived) return false;
      if (!q) return true;
      return c.name.toLowerCase().includes(q);
    });
  });

  readonly total = computed(() => this.filtered().length);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.size()) || 1));
  readonly rows = computed(() => {
    const start = this.page() * this.size();
    return this.filtered().slice(start, start + this.size());
  });

  readonly summary = computed(() => {
    if (this.state() === 'loading') return '';
    return `${this.activeTotal()} active · ${this.archivedTotal()} archived`;
  });

  readonly empty = computed(
    () => this.state() === 'ready' && this.all().length === 0,
  );
  readonly emptyActiveOnly = computed(
    () =>
      this.state() === 'ready' &&
      this.all().length > 0 &&
      this.activeTotal() === 0 &&
      !this.includeArchived() &&
      !this.q(),
  );
  readonly filteredEmpty = computed(
    () =>
      this.state() === 'ready' &&
      this.total() === 0 &&
      !!this.q() &&
      !this.empty() &&
      !this.emptyActiveOnly(),
  );

  ngOnInit(): void {
    if (!this.isAdmin()) {
      this.state.set('forbidden');
      return;
    }

    const nav = this.router.getCurrentNavigation();
    const state =
      (nav?.extras.state as { editCategoryId?: string } | undefined) ??
      (history.state as { editCategoryId?: string } | undefined);

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.includeArchived.set(params.get('includeArchived') === 'true');
      this.q.set(params.get('q') ?? '');
      this.page.set(Number(params.get('page') ?? 0));
      this.size.set(Number(params.get('size') ?? 20));
      if (this.searchCtrl.value !== this.q()) {
        this.searchCtrl.setValue(this.q(), { emitEvent: false });
      }
      if (this.archivedCtrl.value !== this.includeArchived()) {
        this.archivedCtrl.setValue(this.includeArchived(), { emitEvent: false });
      }
      if (this.state() === 'loading' || this.all().length === 0) this.fetch(false);
      else this.clampPage();
    });

    this.searchCtrl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((raw) => this.patchQuery({ q: raw.trim() || null, page: 0 }));

    this.archivedCtrl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((on) => this.patchQuery({ includeArchived: on ? 'true' : null, page: 0 }));

    if (state?.editCategoryId) {
      // Open after first fetch settles
      setTimeout(() => {
        const cat = this.all().find((c) => c.id === state.editCategoryId);
        if (cat) this.openEdit(cat);
      }, 400);
    }
  }

  brandModeLabel(mode?: string): string {
    if (mode === 'CONFIGURED') return 'Configured';
    if (mode === 'FREE_TEXT') return 'Free text';
    return mode || 'Unknown';
  }

  brandModeChip(mode?: string): string {
    return mode === 'CONFIGURED' ? 'chip-primary' : 'chip-neutral';
  }

  brandModeTooltip(mode?: string): string {
    if (mode === 'CONFIGURED') {
      return 'Customers pick from brands you curate, plus Others.';
    }
    return "Customers pick Others or type their own brand.";
  }

  brandCountLabel(c: DeviceCategory): string {
    const n = c.activeBrandCount ?? 0;
    if (c.brandMode === 'FREE_TEXT' && n === 0) return '—';
    return String(n);
  }

  hasMismatch(c: DeviceCategory): boolean {
    return c.brandMode === 'FREE_TEXT' && (c.activeBrandCount ?? 0) > 0;
  }

  refresh(): void {
    this.fetch(true);
  }

  retry(): void {
    this.fetch(false);
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.patchQuery({ q: null, page: 0 });
  }

  showArchived(): void {
    this.archivedCtrl.setValue(true);
  }

  changePage(delta: number): void {
    this.patchQuery({ page: this.page() + delta });
  }

  changeSize(event: Event): void {
    this.patchQuery({ size: Number((event.target as HTMLSelectElement).value), page: 0 });
  }

  openCreate(): void {
    this.openForm();
  }

  openEdit(c: DeviceCategory): void {
    this.openForm(c);
  }

  openArchive(c: DeviceCategory): void {
    this.dialog
      .open(ArchiveCategoryDialogComponent, {
        width: '520px',
        data: { category: c },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((ok) => {
        if (ok !== true) return;
        const ref = this.snack.open(`${c.name} archived`, 'Undo', { duration: 6000 });
        ref.onAction().subscribe(() => {
          this.api.unarchive(c.id).subscribe({
            next: () => {
              this.snack.open(`${c.name} restored`, undefined, { duration: 3000 });
              this.fetch(true);
            },
            error: (err) =>
              this.snack.open(mapHttpError(err).message, undefined, { duration: 4000 }),
          });
        });
        this.fetch(true);
      });
  }

  restore(c: DeviceCategory): void {
    this.api.unarchive(c.id).subscribe({
      next: () => {
        this.snack.open(`${c.name} restored`, undefined, { duration: 3000 });
        this.fetch(true);
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        if (mapped.status === 409) {
          this.snack.open(
            `A different active category is already named '${c.name}'. Rename it first.`,
            undefined,
            { duration: 5000 },
          );
        } else {
          this.snack.open(mapped.message, undefined, { duration: 4000 });
        }
      },
    });
  }

  private openForm(category?: DeviceCategory): void {
    this.dialog
      .open(CategoryFormDialogComponent, {
        width: '520px',
        data: { category },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'stale') {
          this.fetch(true);
          return;
        }
        if (result && typeof result === 'object' && 'id' in result) {
          const saved = result as DeviceCategory;
          const switchedToConfigured =
            saved.brandMode === 'CONFIGURED' &&
            (saved.activeBrandCount ?? 0) === 0 &&
            (!category || category.brandMode !== 'CONFIGURED');
          if (switchedToConfigured) {
            const ref = this.snack.open(`${saved.name} saved`, 'Manage brands', {
              duration: 7000,
            });
            ref.onAction().subscribe(() => {
              void this.router.navigate(['/catalog/categories', saved.id, 'brands']);
            });
          } else {
            this.snack.open(
              category ? `${saved.name} updated` : `${saved.name} created`,
              undefined,
              { duration: 3000 },
            );
          }
          this.fetch(true);
        }
      });
  }

  private clampPage(): void {
    const maxPage = Math.max(0, Math.ceil(this.total() / this.size()) - 1);
    if (this.page() > maxPage) this.patchQuery({ page: maxPage });
  }

  private fetch(isRefresh: boolean): void {
    if (isRefresh && this.state() === 'ready') this.refetching.set(true);
    else this.state.set('loading');

    this.api
      .listAll()
      .pipe(finalize(() => this.refetching.set(false)))
      .subscribe({
        next: (list) => {
          this.all.set(list ?? []);
          this.error.set(null);
          this.state.set('ready');
          this.clampPage();
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
        },
      });
  }

  private patchQuery(patch: Record<string, string | number | null>): void {
    const current = { ...this.route.snapshot.queryParams };
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') delete current[k];
      else current[k] = String(v);
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams: current });
  }
}
