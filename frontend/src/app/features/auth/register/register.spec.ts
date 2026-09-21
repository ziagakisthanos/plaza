import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Register } from './register';
import { AuthService } from '../../../core/services/auth';
import { NO_CONNECTION_MESSAGE } from '../../../core/utils/api-error';

describe('Register', () => {
  let component: Register;
  let register: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  async function open(): Promise<void> {
    register = vi.fn().mockReturnValue(of({}));
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), { provide: AuthService, useValue: { register } }],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component = TestBed.createComponent(Register).componentInstance;
    component.form.setValue({ name: 'Carl', email: 'carl@x.io', password: 'secret12', role: 'CLIENT' });
  }

  it('sends the account and moves on to the sign-in page', async () => {
    await open();

    component.onSubmit();

    expect(register).toHaveBeenCalledWith({ name: 'Carl', email: 'carl@x.io', password: 'secret12', role: 'CLIENT' });
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not send an incomplete form', async () => {
    await open();
    component.form.patchValue({ email: 'not-an-email' });

    component.onSubmit();

    expect(register).not.toHaveBeenCalled();
    expect(component.form.controls.email.touched).toBe(true);
  });

  it('tells the visitor the email is taken', async () => {
    await open();
    register.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'Email already in use: carl@x.io' } })));

    component.onSubmit();

    expect(component.errorMessage()).toBe('An account with this email already exists');
    expect(component.loading()).toBe(false);
  });

  it('shows what the server did not like about the form', async () => {
    await open();
    register.mockReturnValue(
      throwError(() => ({ status: 400, error: { message: 'Validation failed', fieldErrors: { name: 'Name may only contain letters and numbers' } } })),
    );

    component.onSubmit();

    expect(component.errorMessage()).toBe('Name may only contain letters and numbers');
  });

  it('tells the visitor when the server cannot be reached', async () => {
    await open();
    register.mockReturnValue(throwError(() => ({ status: 0 })));

    component.onSubmit();

    expect(component.errorMessage()).toBe(NO_CONNECTION_MESSAGE);
  });

  it('uses a plain message for an unexpected failure', async () => {
    await open();
    register.mockReturnValue(throwError(() => ({ status: 500, error: { message: 'Unexpected error occurred' } })));

    component.onSubmit();

    expect(component.errorMessage()).toBe('Something went wrong. Please try again.');
  });
});
