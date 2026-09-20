export function apiErrorMessage(error: unknown, fallback: string): string {
  const message = (error as { error?: { message?: unknown } } | null)?.error?.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
}
