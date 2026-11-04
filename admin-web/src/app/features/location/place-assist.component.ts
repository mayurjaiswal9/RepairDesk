import { Component, OnDestroy, inject, input, output, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LocationApi, PickedPlace, PlaceSuggestion } from '../../core/api/location.api';
import { mapHttpError } from '../../core/api/api-error';

/**
 * Search list and a paste box for a Maps link the customer sent.
 * Nothing is stored here. The parent saves the pin.
 */
@Component({
  selector: 'app-place-assist',
  standalone: true,
  imports: [MatSnackBarModule],
  templateUrl: './place-assist.component.html',
  styleUrl: './place-assist.component.scss',
})
export class PlaceAssistComponent implements OnDestroy {
  private readonly locationApi = inject(LocationApi);
  private readonly snack = inject(MatSnackBar);

  readonly biasLatitude = input<number | null>(null);
  readonly biasLongitude = input<number | null>(null);
  readonly placePicked = output<PickedPlace>();
  readonly queryChange = output<string>();

  readonly query = signal('');
  readonly pasted = signal('');
  readonly suggestions = signal<PlaceSuggestion[]>([]);
  readonly searching = signal(false);
  readonly readingLink = signal(false);

  private sessionToken = crypto.randomUUID();
  private lastSentAt = 0;
  private lastSentText = '';
  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    this.clearTimer();
  }

  onQuery(value: string): void {
    this.query.set(value);
    this.queryChange.emit(value);
    const text = value.trim();
    if (text.length < 5) {
      this.suggestions.set([]);
      this.clearTimer();
      return;
    }
    this.arm();
  }

  clearQuery(): void {
    this.query.set('');
    this.suggestions.set([]);
    this.clearTimer();
    this.queryChange.emit('');
  }

  choose(suggestion: PlaceSuggestion): void {
    this.searching.set(true);
    this.locationApi.selectPlace(suggestion.placeId, this.sessionToken).subscribe({
      next: (hit) => {
        this.searching.set(false);
        this.placePicked.emit({
          latitude: hit.latitude,
          longitude: hit.longitude,
          addressLine: hit.formattedAddress?.trim() || suggestion.primaryText,
          pinSource: 'GEOCODED',
        });
        this.clearQuery();
        this.sessionToken = crypto.randomUUID();
        this.lastSentAt = 0;
        this.lastSentText = '';
      },
      error: (err) => {
        this.searching.set(false);
        this.snack.open(mapHttpError(err).message, 'Dismiss', { duration: 5000 });
      },
    });
  }

  useCustomerLink(): void {
    const text = this.pasted().trim();
    if (!text) return;
    this.readingLink.set(true);
    this.locationApi.customerPin(text).subscribe({
      next: (hit) => {
        this.readingLink.set(false);
        this.placePicked.emit({
          latitude: hit.latitude,
          longitude: hit.longitude,
          addressLine: hit.formattedAddress?.trim() || '',
          pinSource: 'CUSTOMER_LINK',
        });
        this.pasted.set('');
        this.clearQuery();
      },
      error: (err) => {
        this.readingLink.set(false);
        this.snack.open(mapHttpError(err).message, 'Dismiss', { duration: 5000 });
      },
    });
  }

  private arm(): void {
    this.clearTimer();
    const since = Date.now() - this.lastSentAt;
    const wait = this.lastSentAt === 0 || since >= 5000 ? 0 : 5000 - since;
    this.timer = setTimeout(() => this.send(), wait);
  }

  private send(): void {
    const text = this.query().trim();
    if (text.length < 5 || text === this.lastSentText) return;
    this.lastSentAt = Date.now();
    this.lastSentText = text;
    this.searching.set(true);
    const lat = this.biasLatitude();
    const lng = this.biasLongitude();
    this.locationApi.suggest(text, this.sessionToken, lat, lng).subscribe({
      next: (response) => {
        this.searching.set(false);
        if (this.query().trim() !== text) {
          this.arm();
          return;
        }
        this.suggestions.set(response.suggestions ?? []);
      },
      error: (err) => {
        this.searching.set(false);
        this.suggestions.set([]);
        this.snack.open(mapHttpError(err).message, 'Dismiss', { duration: 5000 });
      },
    });
  }

  private clearTimer(): void {
    if (this.timer != null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
