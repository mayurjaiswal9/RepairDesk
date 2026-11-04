import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type TripStatus = 'ACTIVE' | 'ENDED' | 'CANCELLED';
export type TripStartKind = 'SHOP' | 'PREVIOUS_JOB' | 'OTHER' | 'UNKNOWN';
export type VehicleType = 'TWO_WHEELER' | 'FOUR_WHEELER';
export type TripFlag =
  | 'LONGER_THAN_ROUTE'
  | 'SHORTER_THAN_ROUTE'
  | 'MOCKED'
  | 'GAPS'
  | 'FAR_FROM_JOB'
  | 'NO_POINTS';
export type NoTripReasonCode = 'AT_SHOP' | 'ALREADY_ON_SITE' | 'PHONE_PROBLEM' | 'OTHER';
export type MileageShow = 'ALL' | 'FLAGGED' | 'NO_TRIP';
export type MileageView = 'DAY' | 'MONTH';
export type MileageExportLevel = 'DAY' | 'TRIP';

export interface GeoPoint {
  latitude: number;
  longitude: number;
}

export interface TripResponse {
  id: string;
  jobId: string;
  jobReference: string;
  technicianId: string;
  technicianName: string;
  status: TripStatus | string;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number;
  startKind: TripStartKind | string;
  startJobReference: string | null;
  start: GeoPoint | null;
  end: GeoPoint | null;
  destination: GeoPoint | null;
  trackedDistanceM: number;
  estimatedGapM: number;
  plannedDistanceM: number | null;
  plannedDurationSeconds: number | null;
  plannedEstimated: boolean;
  adjustedDistanceM: number | null;
  adjustReason: string | null;
  adjustedByName: string | null;
  adjustedAt: string | null;
  payableDistanceM: number;
  vehicleType: VehicleType | string | null;
  ratePaise: number | null;
  amountPaise: number | null;
  flags: (TripFlag | string)[];
  pointCount: number;
  endReason: string | null;
  /** [latitude, longitude] pairs; empty outside job travel and day detail. */
  route: [number, number][];
}

export interface MileageSummary {
  payableDistanceM: number;
  trips: number;
  amountPaise: number;
  flaggedTrips: number;
  noTripJobs: number;
  activeTrips: number;
}

export interface MileageRow {
  date: string | null;
  month: string | null;
  technicianId: string;
  technicianName: string;
  vehicleType: VehicleType | string | null;
  trips: number;
  trackedDistanceM: number;
  adjustmentM: number;
  payableDistanceM: number;
  ratePaise: number | null;
  amountPaise: number;
  flaggedTrips: number;
  noTripJobs: number;
  activeTrips: number;
}

export interface MileageDaysResponse {
  from: string;
  to: string;
  view: MileageView;
  summary: MileageSummary;
  rows: MileageRow[];
}

export interface NoTripJob {
  jobId: string;
  referenceNumber: string;
  reasonCode: NoTripReasonCode | string;
  reasonText: string | null;
  createdAt: string;
}

export interface MileageDayDetail {
  date: string;
  technicianId: string;
  technicianName: string;
  vehicleType: VehicleType | string | null;
  shop: GeoPoint | null;
  summary: MileageSummary;
  trips: TripResponse[];
  noTripJobs: NoTripJob[];
}

export interface JobNoTrip {
  reasonCode: NoTripReasonCode | string;
  reasonText: string | null;
  technicianName: string | null;
  createdAt: string;
}

export interface JobTravelResponse {
  trips: TripResponse[];
  noTrip: JobNoTrip | null;
}

export interface MileageQuery {
  from: string;
  to: string;
  technicianId?: string | null;
  vehicle?: VehicleType | null;
  show?: MileageShow | null;
  view?: MileageView | null;
}

@Injectable({ providedIn: 'root' })
export class MileageApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  days(query: MileageQuery): Observable<MileageDaysResponse> {
    let params = filterParams(query);
    if (query.view) params = params.set('view', query.view);
    return this.http.get<MileageDaysResponse>(`${this.base}/mileage/days`, { params });
  }

  day(technicianId: string, date: string): Observable<MileageDayDetail> {
    return this.http.get<MileageDayDetail>(
      `${this.base}/mileage/days/${encodeURIComponent(technicianId)}/${encodeURIComponent(date)}`,
    );
  }

  export(query: MileageQuery, level: MileageExportLevel): Observable<Blob> {
    const params = filterParams(query).set('level', level);
    return this.http.get(`${this.base}/mileage/export.csv`, { params, responseType: 'blob' });
  }

  jobTrips(jobId: string): Observable<JobTravelResponse> {
    return this.http.get<JobTravelResponse>(`${this.base}/jobs/${jobId}/trips`);
  }

  adjustDistance(tripId: string, distanceKm: number, reason: string): Observable<TripResponse> {
    return this.http.put<TripResponse>(`${this.base}/trips/${tripId}/distance`, { distanceKm, reason });
  }
}

function filterParams(query: MileageQuery): HttpParams {
  let params = new HttpParams().set('from', query.from).set('to', query.to);
  if (query.technicianId) params = params.set('technicianId', query.technicianId);
  if (query.vehicle) params = params.set('vehicle', query.vehicle);
  if (query.show && query.show !== 'ALL') params = params.set('show', query.show);
  return params;
}
