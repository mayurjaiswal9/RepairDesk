import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody } from '../models/auth.models';

export interface MappedApiError {
  status: number;
  errorCode?: string;
  message: string;
  correlationId?: string;
  fieldErrors: Record<string, string>;
  raw: HttpErrorResponse;
}

export function mapHttpError(err: unknown, fallback = 'Something went wrong. Please try again.'): MappedApiError {
  if (!(err instanceof HttpErrorResponse)) {
    return {
      status: 0,
      message: "Can't reach RepairDesk. Check your connection and try again.",
      fieldErrors: {},
      raw: err as HttpErrorResponse,
    };
  }

  if (err.status === 0) {
    return {
      status: 0,
      message: "Can't reach RepairDesk. Check your connection and try again.",
      fieldErrors: {},
      raw: err,
    };
  }

  const body = (err.error ?? {}) as ApiErrorBody;
  const fieldErrors: Record<string, string> = {};
  for (const fe of body.fieldErrors ?? []) {
    if (fe.field) fieldErrors[fe.field] = fe.message;
  }

  return {
    status: err.status,
    errorCode: body.errorCode,
    message: body.message || fallback,
    correlationId: body.correlationId,
    fieldErrors,
    raw: err,
  };
}
