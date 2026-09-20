import { apiErrorMessage } from './api-error';

describe('apiErrorMessage', () => {
  it('uses the message sent by the server', () => {
    const error = { status: 409, error: { message: "Not enough stock for 'Book': only 1 left" } };

    expect(apiErrorMessage(error, 'fallback')).toBe("Not enough stock for 'Book': only 1 left");
  });

  it('falls back when the server sent no message', () => {
    expect(apiErrorMessage({ status: 500, error: {} }, 'fallback')).toBe('fallback');
    expect(apiErrorMessage({ status: 0, error: null }, 'fallback')).toBe('fallback');
    expect(apiErrorMessage({ error: { message: '   ' } }, 'fallback')).toBe('fallback');
    expect(apiErrorMessage({ error: { message: 42 } }, 'fallback')).toBe('fallback');
  });

  it('falls back for values that are not errors', () => {
    expect(apiErrorMessage(null, 'fallback')).toBe('fallback');
    expect(apiErrorMessage(undefined, 'fallback')).toBe('fallback');
    expect(apiErrorMessage('boom', 'fallback')).toBe('fallback');
  });
});
