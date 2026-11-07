import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface GeocodeHit {
  latitude: number;
  longitude: number;
  formattedAddress?: string | null;
}

export interface PlaceSuggestion {
  placeId: string;
  primaryText: string;
  secondaryText?: string | null;
}

export interface PickedPlace {
  latitude: number;
  longitude: number;
  addressLine: string;
  pinSource: 'GEOCODED' | 'CUSTOMER_LINK';
}

export interface LocationSettings {
  shopLatitude?: number | null;
  shopLongitude?: number | null;
  geofenceRadiusM: number;
  checkInEnabled: boolean;
  attendanceEnabled: boolean;
  trackingEnabled: boolean;
  travelEnabled: boolean;
  etaEnabled: boolean;
  pingIntervalMinutes: number;
  pingDistanceM: number;
  dayAutoEndTime: string;
  allowStartOutsideGeofence: boolean;
  farFromAddressM: number;
  rateTwoWheelerPaise: number;
  rateFourWheelerPaise: number;
  retentionDays: number;
  privacyNoticeVersion: string;
  /** In the PUT body, null or missing keeps the stored value. */
  strictTripMode?: boolean | null;
  tripOpenMaps?: boolean | null;
  travelPingSeconds?: number | null;
  onSitePingMinutes?: number | null;
}

export interface LocationConfig {
  attendanceEnabled: boolean;
  trackingEnabled: boolean;
  travelEnabled: boolean;
  strictTripMode?: boolean | null;
  tripOpenMaps?: boolean | null;
  travelPingSeconds?: number | null;
  onSitePingMinutes?: number | null;
}

export type TechnicianState = 'TRAVELLING' | 'ON_SITE';

export interface LocationHistory {
  date: string;
  days: string[];
  locations: {
    id: string;
    technicianId: string;
    technicianName: string;
    latitude: number;
    longitude: number;
    recordedAt: string;
    jobId?: string | null;
    jobReference?: string | null;
    state?: TechnicianState | string | null;
  }[];
}

export interface LocationBoard {
  shop: { latitude?: number | null; longitude?: number | null; radiusM: number };
  technicians: {
    id: string;
    name: string;
    latitude?: number | null;
    longitude?: number | null;
    recordedAt?: string | null;
    jobId?: string | null;
    state?: TechnicianState | string | null;
  }[];
  jobs: {
    id: string;
    referenceNumber: string;
    address?: string | null;
    latitude?: number | null;
    longitude?: number | null;
    technicianId?: string | null;
    technicianName?: string | null;
    etaMinutes?: number | null;
    distanceMetres?: number | null;
    etaEstimated: boolean;
    etaFrom: 'TECHNICIAN' | 'OFFICE' | 'NONE' | string;
  }[];
}

@Injectable({ providedIn: 'root' })
export class LocationApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  geocode(address: string): Observable<GeocodeHit> {
    return this.http.post<GeocodeHit>(`${this.base}/geocode`, { address });
  }

  suggest(
    input: string,
    sessionToken: string,
    latitude?: number | null,
    longitude?: number | null,
  ): Observable<{ suggestions: PlaceSuggestion[] }> {
    return this.http.post<{ suggestions: PlaceSuggestion[] }>(`${this.base}/location/suggestions`, {
      input,
      sessionToken,
      ...(latitude != null && longitude != null ? { latitude, longitude } : {}),
    });
  }

  selectPlace(placeId: string, sessionToken: string): Observable<GeocodeHit> {
    return this.http.post<GeocodeHit>(`${this.base}/location/suggestions/select`, { placeId, sessionToken });
  }

  customerPin(text: string): Observable<GeocodeHit> {
    return this.http.post<GeocodeHit>(`${this.base}/location/customer-pin`, { text });
  }

  getSettings(): Observable<LocationSettings> {
    return this.http.get<LocationSettings>(`${this.base}/settings/location`);
  }

  updateSettings(body: LocationSettings): Observable<LocationSettings> {
    return this.http.put<LocationSettings>(`${this.base}/settings/location`, body);
  }

  board(): Observable<LocationBoard> {
    return this.http.get<LocationBoard>(`${this.base}/location/board`);
  }

  history(date?: string | null): Observable<LocationHistory> {
    const params = date ? { date } : undefined;
    return this.http.get<LocationHistory>(`${this.base}/location/board/history`, { params });
  }

  config(): Observable<LocationConfig> {
    return this.http.get<LocationConfig>(`${this.base}/location/config`);
  }
}
