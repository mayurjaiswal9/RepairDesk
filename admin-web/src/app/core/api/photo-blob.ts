/** Build an object URL browsers can actually render. */
export function objectUrlForPhoto(blob: Blob, fallbackType?: string): string {
  const stored = (fallbackType || '').toLowerCase().split(';')[0].trim();
  const incoming = (blob.type || '').toLowerCase().split(';')[0].trim();
  const usable =
    displayableImageType(incoming) ??
    displayableImageType(stored) ??
    'image/jpeg';
  const typed = incoming === usable ? blob : new Blob([blob], { type: usable });
  return URL.createObjectURL(typed);
}

function displayableImageType(type: string): string | null {
  if (type === 'image/jpeg' || type === 'image/png' || type === 'image/gif' || type === 'image/webp') {
    return type;
  }
  if (type === 'image/jpg') return 'image/jpeg';
  return null;
}
