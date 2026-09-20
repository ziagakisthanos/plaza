import { NO_CONNECTION_MESSAGE, apiErrorMessage } from './api-error';

describe('apiErrorMessage', () => {
  it('uses the message sent by the server', () => {
    const error = { status: 409, error: { message: "Not enough stock for 'Book': only 1 left" } };

    expect(apiErrorMessage(error, 'fallback')).toBe("Not enough stock for 'Book': only 1 left");
  });

  it('lists what is wrong with each field, when the server validated a form', () => {
    const error = {
      status: 400,
      error: {
        message: 'Validation failed',
        fieldErrors: { category: 'Please choose a category', price: 'Price must be greater than 0' },
      },
    };

    expect(apiErrorMessage(error, 'fallback')).toBe('Please choose a category; Price must be greater than 0');
  });

  it('ignores field errors that are empty or not text', () => {
    const error = { status: 400, error: { message: 'Validation failed', fieldErrors: { a: '', b: 5, c: null } } };

    expect(apiErrorMessage(error, 'fallback')).toBe('Validation failed');
  });

  it('says the server cannot be reached when there was no answer at all', () => {
    expect(apiErrorMessage({ status: 0, error: null }, 'fallback')).toBe(NO_CONNECTION_MESSAGE);
    expect(apiErrorMessage({ status: 0 }, 'fallback')).toContain("can't reach the server");
  });

  it('keeps the friendly explanation when the service is temporarily unavailable', () => {
    const error = { status: 503, error: { message: 'Products are temporarily unavailable, please try again' } };

    expect(apiErrorMessage(error, 'fallback')).toBe('Products are temporarily unavailable, please try again');
  });

  it('never shows the raw text of an unexpected server error', () => {
    const error = { status: 500, error: { message: 'Unexpected error occurred' } };

    expect(apiErrorMessage(error, 'We could not add the item to your cart.')).toBe(
      'We could not add the item to your cart.',
    );
  });

  it('falls back when the server sent no message', () => {
    expect(apiErrorMessage({ status: 404, error: {} }, 'fallback')).toBe('fallback');
    expect(apiErrorMessage({ status: 500 }, 'fallback')).toBe('fallback');
    expect(apiErrorMessage({ error: { message: '   ' } }, 'fallback')).toBe('fallback');
    expect(apiErrorMessage({ error: { message: 42 } }, 'fallback')).toBe('fallback');
  });

  it('falls back for values that are not errors', () => {
    expect(apiErrorMessage(null, 'fallback')).toBe('fallback');
    expect(apiErrorMessage(undefined, 'fallback')).toBe('fallback');
    expect(apiErrorMessage('boom', 'fallback')).toBe('fallback');
  });
});
