import { Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LocationApi, LocationSettings } from '../../core/api/location.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { MapPin, PinMapComponent } from '../../core/maps/pin-map.component';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

@Component({
  selector: 'app-location-settings',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatSnackBarModule, PinMapComponent],
  templateUrl: './location-settings.component.html',
  styleUrl: './location-settings.component.scss',
})
export class LocationSettingsComponent implements OnInit {
  private readonly api = inject(LocationApi);
  private readonly fb = inject(FormBuilder);
  private readonly snack = inject(MatSnackBar);

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly saving = signal(false);
  readonly searching = signal(false);
  readonly pins = signal<MapPin[]>([]);

  readonly form = this.fb.nonNullable.group({
    shopSearch: [''],
    shopLatitude: [''],
    shopLongitude: [''],
    geofenceRadiusM: [150, [Validators.required, Validators.min(50), Validators.max(1000)]],
    checkInEnabled: [false],
    attendanceEnabled: [false],
    trackingEnabled: [false],
    travelEnabled: [false],
    etaEnabled: [false],
    pingIntervalMinutes: [30, [Validators.required, Validators.min(1), Validators.max(30)]],
    pingDistanceM: [200, [Validators.required, Validators.min(50), Validators.max(5000)]],
    dayAutoEndTime: ['20:00', Validators.required],
    allowStartOutsideGeofence: [false],
    farFromAddressM: [300, [Validators.required, Validators.min(50), Validators.max(5000)]],
    rateTwoWheeler: [5, [Validators.required, Validators.min(0)]],
    rateFourWheeler: [10, [Validators.required, Validators.min(0)]],
    retentionDays: [90, [Validators.required, Validators.min(7), Validators.max(365)]],
    privacyNoticeVersion: ['1', Validators.required],
    strictTripMode: [{ value: true, disabled: true }],
    tripOpenMaps: [true],
    travelPingSeconds: [120, [Validators.required, Validators.min(30), Validators.max(600)]],
    onSitePingMinutes: [30, [Validators.required, Validators.min(15), Validators.max(240)]],
  });

  constructor() {
    this.form.controls.travelEnabled.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((on) => this.syncStrictMode(on));
  }

  ngOnInit(): void {
    this.api.getSettings().subscribe({
      next: (settings) => {
        this.apply(settings);
        this.state.set('ready');
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        this.error.set(mapped);
        this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
      },
    });
  }

  searchOffice(): void {
    const query = this.form.controls.shopSearch.value.trim();
    if (!query) return;
    this.searching.set(true);
    this.api.geocode(query).subscribe({
      next: (hit) => {
        this.form.patchValue({
          shopLatitude: String(hit.latitude),
          shopLongitude: String(hit.longitude),
        });
        this.refreshPins();
        this.searching.set(false);
      },
      error: (err) => {
        this.searching.set(false);
        this.snack.open(mapHttpError(err).message, 'Dismiss', { duration: 5000 });
      },
    });
  }

  onMapClick(point: { latitude: number; longitude: number }): void {
    this.form.patchValue({
      shopLatitude: point.latitude.toFixed(6),
      shopLongitude: point.longitude.toFixed(6),
    });
    this.refreshPins();
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.snack.open('Some values are out of range. Check the highlighted fields.', 'Dismiss', { duration: 4000 });
      return;
    }
    const v = this.form.getRawValue();
    const lat = v.shopLatitude.trim();
    const lng = v.shopLongitude.trim();
    if ((lat === '') !== (lng === '')) {
      this.snack.open('Set both latitude and longitude, or clear both.', 'Dismiss', { duration: 4000 });
      return;
    }
    const body: LocationSettings = {
      shopLatitude: lat === '' ? null : Number(lat),
      shopLongitude: lng === '' ? null : Number(lng),
      geofenceRadiusM: v.geofenceRadiusM,
      checkInEnabled: v.checkInEnabled,
      attendanceEnabled: v.attendanceEnabled,
      trackingEnabled: v.trackingEnabled,
      travelEnabled: v.travelEnabled,
      etaEnabled: v.etaEnabled,
      pingIntervalMinutes: v.pingIntervalMinutes,
      pingDistanceM: v.pingDistanceM,
      dayAutoEndTime: v.dayAutoEndTime.length === 5 ? `${v.dayAutoEndTime}:00` : v.dayAutoEndTime,
      allowStartOutsideGeofence: v.allowStartOutsideGeofence,
      farFromAddressM: v.farFromAddressM,
      rateTwoWheelerPaise: Math.round(v.rateTwoWheeler * 100),
      rateFourWheelerPaise: Math.round(v.rateFourWheeler * 100),
      retentionDays: v.retentionDays,
      privacyNoticeVersion: v.privacyNoticeVersion.trim(),
      strictTripMode: v.strictTripMode,
      tripOpenMaps: v.tripOpenMaps,
      travelPingSeconds: v.travelPingSeconds,
      onSitePingMinutes: v.onSitePingMinutes,
    };
    this.saving.set(true);
    this.api.updateSettings(body).subscribe({
      next: (saved) => {
        this.apply(saved);
        this.saving.set(false);
        this.snack.open('Office location saved', 'Dismiss', { duration: 3000 });
      },
      error: (err) => {
        this.saving.set(false);
        this.snack.open(mapHttpError(err).message, 'Dismiss', { duration: 5000 });
      },
    });
  }

  private apply(settings: LocationSettings): void {
    const time = (settings.dayAutoEndTime || '20:00').slice(0, 5);
    this.form.patchValue({
      shopLatitude: settings.shopLatitude == null ? '' : String(settings.shopLatitude),
      shopLongitude: settings.shopLongitude == null ? '' : String(settings.shopLongitude),
      geofenceRadiusM: settings.geofenceRadiusM,
      checkInEnabled: settings.checkInEnabled,
      attendanceEnabled: settings.attendanceEnabled,
      trackingEnabled: settings.trackingEnabled,
      travelEnabled: settings.travelEnabled,
      etaEnabled: settings.etaEnabled,
      pingIntervalMinutes: settings.pingIntervalMinutes,
      pingDistanceM: settings.pingDistanceM,
      dayAutoEndTime: time,
      allowStartOutsideGeofence: settings.allowStartOutsideGeofence,
      farFromAddressM: settings.farFromAddressM,
      rateTwoWheeler: settings.rateTwoWheelerPaise / 100,
      rateFourWheeler: settings.rateFourWheelerPaise / 100,
      retentionDays: settings.retentionDays,
      privacyNoticeVersion: settings.privacyNoticeVersion,
      strictTripMode: settings.strictTripMode ?? true,
      tripOpenMaps: settings.tripOpenMaps ?? true,
      travelPingSeconds: settings.travelPingSeconds ?? 120,
      onSitePingMinutes: settings.onSitePingMinutes ?? 30,
    });
    this.syncStrictMode(settings.travelEnabled);
    this.refreshPins();
  }

  private syncStrictMode(travelEnabled: boolean): void {
    const control = this.form.controls.strictTripMode;
    if (travelEnabled) control.enable({ emitEvent: false });
    else control.disable({ emitEvent: false });
  }

  private refreshPins(): void {
    const lat = Number(this.form.controls.shopLatitude.value);
    const lng = Number(this.form.controls.shopLongitude.value);
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || this.form.controls.shopLatitude.value === '') {
      this.pins.set([]);
      return;
    }
    this.pins.set([{ id: 'office', latitude: lat, longitude: lng, title: 'Office', kind: 'office' }]);
  }
}
