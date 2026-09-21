export const NO_CONNECTION_MESSAGE = "We can't reach the server. Please check your connection and try again.";

interface ApiFailure {
  status?: number;
  error?: { message?: unknown; fieldErrors?: unknown } | null;
}

function fieldMessages(fieldErrors: unknown): string {
  if (!fieldErrors || typeof fieldErrors !== 'object') return '';
  return Object.values(fieldErrors)
    .filter((message): message is string => typeof message === 'string' && message.trim().length > 0)
    .join('; ');
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  const failure = error as ApiFailure | null;
  if (failure?.status === 0) return NO_CONNECTION_MESSAGE;

  const fields = fieldMessages(failure?.error?.fieldErrors);
  if (fields) return fields;

  const message = failure?.error?.message;
  const usable = typeof message === 'string' && message.trim().length > 0 && failure?.status !== 500;
  return usable ? message : fallback;
}
