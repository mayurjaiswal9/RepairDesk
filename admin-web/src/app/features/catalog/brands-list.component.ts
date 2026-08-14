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
import { debounceTime, distinctUntilChanged, finalize, forkJoin } from 'rxjs';
import { CategoriesApi } from '../../core/api/categories.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { DeviceBrand, DeviceCategory } from '../../core/models/request.models';
import {
  ArchiveBrandDialogComponent,
  BrandFormDialogComponent,
  CategoryFormDialogComponent,
  SwitchBrandModeDialogComponent,
} from './dialogs/catalog-dialogs';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden' | 'notfound';

@Component({
  selector: 'app-brands-list',
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
  templateUrl: './brands-list.component.html',
  styleUrl: './brands-list.component.scss',
})
export class BrandsListComponent implements OnInit {
  private readonly api = inject(CategoriesApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly category = signal<DeviceCategory | null>(null);
  readonly all = signal<DeviceBrand[]>([]);
  readonly error = signal<MappedApiError | null>(null);
  readonly includeArchived = signal(false);
  readonly q = signal('');
  readonly categoryId = signal('');

  readonly searchCtrl = new FormControl('', { nonNullable: true });
  readonly archivedCtrl = new FormControl(false, { nonNullable: true });

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');
  readonly categoryName = computed(() => this.category()?.name ?? '…');
  readonly activeTotal = computed(() => this.all().filter((b) => !b.archived).length);
  readonly archivedTotal = computed(() => this.all().filter((b) => b.archived).length);
  readonly isFreeText = computed(() => this.category()?.brandMode === 'FREE_TEXT');
  readonly isParentArchived = computed(() => !!this.category()?.archived);

  readonly filtered = computed(() => {
    const q = this.q().toLowerCase();
    const showArchived = this.includeArchived();
    return this.all().filter((b) => {
      if (!showArchived && b.archived) return false;
      if (!q) return true;
      return b.name.toLowerCase().includes(q);
    });
  });

  readonly summary = computed(() => {
    if (this.state() !== 'ready') return '';
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
    () => this.state() === 'ready' && this.filtered().length === 0 && !!this.q(),
  );

  ngOnInit(): void {
    if (!this.isAdmin()) {
      this.state.set('forbidden');
      return;
    }

    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id') ?? '';
      this.categoryId.set(id);
      this.fetch(false);
    });

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.includeArchived.set(params.get('includeArchived') === 'true');
      this.q.set(params.get('q') ?? '');
      if (this.searchCtrl.value !== this.q()) {
        this.searchCtrl.setValue(this.q(), { emitEvent: false });
      }
      if (this.archivedCtrl.value !== this.includeArchived()) {
        this.archivedCtrl.setValue(this.includeArchived(), { emitEvent: false });
      }
    });

    this.searchCtrl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((raw) => this.patchQuery({ q: raw.trim() || null }));

    this.archivedCtrl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((on) => this.patchQuery({ includeArchived: on ? 'true' : null }));
  }

  refresh(): void {
    this.fetch(true);
  }

  retry(): void {
    this.fetch(false);
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.patchQuery({ q: null });
  }

  showArchived(): void {
    this.archivedCtrl.setValue(true);
  }

  openCreate(): void {
    this.openBrandForm();
  }

  openEdit(b: DeviceBrand): void {
    if (b.archived) return;
    this.openBrandForm(b);
  }

  openArchive(b: DeviceBrand): void {
    const cat = this.category();
    if (!cat) return;
    this.dialog
      .open(ArchiveBrandDialogComponent, {
        width: '520px',
        data: { category: cat, brand: b },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((ok) => {
        if (ok !== true) return;
        const ref = this.snack.open(`${b.name} archived`, 'Undo', { duration: 6000 });
        ref.onAction().subscribe(() => {
          this.api.unarchiveBrand(cat.id, b.id).subscribe({
            next: () => {
              this.snack.open(`${b.name} restored`, undefined, { duration: 3000 });
              this.fetch(true);
            },
            error: (err) =>
              this.snack.open(mapHttpError(err).message, undefined, { duration: 4000 }),
          });
        });
        this.fetch(true);
      });
  }

  restore(b: DeviceBrand): void {
    const cat = this.category();
    if (!cat) return;
    this.api.unarchiveBrand(cat.id, b.id).subscribe({
      next: () => {
        this.snack.open(`${b.name} restored`, undefined, { duration: 3000 });
        this.fetch(true);
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        if (mapped.status === 409) {
          this.snack.open(
            `${cat.name} already has an active brand named '${b.name}'. Rename or archive that one first.`,
            undefined,
            { duration: 5000 },
          );
        } else {
          this.snack.open(mapped.message, undefined, { duration: 4000 });
        }
      },
    });
  }

  switchToConfigured(): void {
    const cat = this.category();
    if (!cat || this.activeTotal() === 0) return;
    this.dialog
      .open(SwitchBrandModeDialogComponent, {
        width: '520px',
        data: { category: cat, activeCount: this.activeTotal() },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result && typeof result === 'object' && 'id' in result) {
          this.category.set(result as DeviceCategory);
          this.snack.open(`${cat.name} now uses a configured brand list`, undefined, {
            duration: 4000,
          });
        }
      });
  }

  editCategory(): void {
    const cat = this.category();
    if (!cat) return;
    this.dialog
      .open(CategoryFormDialogComponent, {
        width: '520px',
        data: { category: cat },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result && typeof result === 'object' && 'id' in result) {
          this.category.set(result as DeviceCategory);
          this.snack.open(`${cat.name} updated`, undefined, { duration: 3000 });
        } else if (result === 'stale') {
          this.fetch(true);
        }
      });
  }

  private openBrandForm(brand?: DeviceBrand): void {
    const cat = this.category();
    if (!cat) return;
    this.dialog
      .open(BrandFormDialogComponent, {
        width: '480px',
        data: {
          category: cat,
          brand,
          onCreated: (created: DeviceBrand) => {
            this.snack.open(`${created.name} added to ${cat.name}`, undefined, {
              duration: 3000,
            });
            this.fetch(true);
          },
        },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result && typeof result === 'object' && 'id' in result) {
          const saved = result as DeviceBrand;
          this.snack.open(
            brand
              ? `${saved.name} updated`
              : `${saved.name} added to ${cat.name}`,
            undefined,
            { duration: 3000 },
          );
          this.fetch(true);
        }
      });
  }

  private fetch(isRefresh: boolean): void {
    const id = this.categoryId();
    if (!id) {
      this.state.set('notfound');
      return;
    }
    if (isRefresh && this.state() === 'ready') this.refetching.set(true);
    else this.state.set('loading');

    forkJoin({
      categories: this.api.listAll(),
      brands: this.api.listBrandsAll(id),
    })
      .pipe(finalize(() => this.refetching.set(false)))
      .subscribe({
        next: ({ categories, brands }) => {
          const cat = (categories ?? []).find((c) => c.id === id) ?? null;
          if (!cat) {
            this.state.set('notfound');
            return;
          }
          this.category.set(cat);
          this.all.set(brands ?? []);
          this.error.set(null);
          this.state.set('ready');
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          if (mapped.status === 404) this.state.set('notfound');
          else if (mapped.status === 403) this.state.set('forbidden');
          else this.state.set('error');
        },
      });
  }

  private patchQuery(patch: Record<string, string | null>): void {
    const current = { ...this.route.snapshot.queryParams };
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') delete current[k];
      else current[k] = v;
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams: current });
  }
}
