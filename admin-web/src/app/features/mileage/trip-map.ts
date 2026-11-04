import { GeoPoint, TripResponse } from '../../core/api/mileage.api';
import { MapRoute, RouteMapPin } from '../../core/maps/route-map.component';
import { tripColor } from './mileage-format';

export function tripRoutes(trips: TripResponse[]): MapRoute[] {
  return trips.map((trip, index) => ({
    id: trip.id,
    label: String(index + 1),
    color: tripColor(index, trip.status),
    path: trip.route ?? [],
    start: trip.start,
    dashed: trip.status === 'CANCELLED',
  }));
}

export function tripPins(trips: TripResponse[], shop?: GeoPoint | null): RouteMapPin[] {
  const pins: RouteMapPin[] = [];
  if (shop?.latitude != null && shop.longitude != null) {
    pins.push({ id: 'shop', latitude: shop.latitude, longitude: shop.longitude, title: 'Shop', kind: 'shop' });
  }
  const seen = new Set<string>();
  for (const trip of trips) {
    const pin = trip.destination;
    if (pin?.latitude == null || pin.longitude == null || seen.has(trip.jobId)) continue;
    seen.add(trip.jobId);
    pins.push({ id: `job-${trip.jobId}`, latitude: pin.latitude, longitude: pin.longitude, title: trip.jobReference, kind: 'job' });
  }
  return pins;
}
