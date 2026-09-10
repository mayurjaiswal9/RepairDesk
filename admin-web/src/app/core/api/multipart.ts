/** Safe multipart filename — commas/spaces in exports (e.g. ChatGPT) break some parsers. */
export function safeUploadFilename(file: File): string {
  const raw = (file.name ?? '').trim() || 'photo';
  const cleaned = raw.replace(/[^A-Za-z0-9._-]+/g, '_').replace(/_+/g, '_').replace(/^_+|_+$/g, '');
  return (cleaned || 'photo').slice(0, 80);
}

export function toFileFormData(file: File): FormData {
  const data = new FormData();
  data.append('file', file, safeUploadFilename(file));
  return data;
}
