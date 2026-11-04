/** Opens turn-by-turn directions in Google Maps (app on a phone, browser tab on desktop). */
export function googleMapsDirectionsUrl(destination: {
  latitude?: number | null;
  longitude?: number | null;
  address?: string | null;
}): string | null {
  const lat = destination.latitude;
  const lng = destination.longitude;
  const params = new URLSearchParams({ api: '1', travelmode: 'driving' });
  if (lat != null && lng != null && Number.isFinite(lat) && Number.isFinite(lng)) {
    params.set('destination', `${lat},${lng}`);
  } else {
    const address = destination.address?.trim();
    if (!address) return null;
    params.set('destination', address);
  }
  return `https://www.google.com/maps/dir/?${params.toString()}`;
}
