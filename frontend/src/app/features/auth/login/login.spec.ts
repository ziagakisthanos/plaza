import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { Login } from './login';
import { AuthService } from '../../../core/services/auth';
import { NO_CONNECTION_MESSAGE } from '../../../core/utils/api-error';
import { of, throwError } from 'rxjs';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('when signing in', () => {
    let login: ReturnType<typeof vi.fn>;
    let signIn: Login;

    beforeEach(() => {
      login = vi.fn().mockReturnValue(of({ token: 't' }));
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [Login],
        providers: [provideRouter([]), { provide: AuthService, useValue: { login } }],
      });
      signIn = TestBed.createComponent(Login).componentInstance;
      signIn.form.setValue({ email: 'carl@x.io', password: 'secret12' });
    });

    it('says the credentials are wrong without giving anything away', () => {
      login.mockReturnValue(throwError(() => ({ status: 401, error: { message: 'Invalid email or password' } })));

      signIn.onSubmit();

      expect(signIn.errorMessage()).toBe('Invalid email or password');
      expect(signIn.loading()).toBe(false);
    });

    it('tells the visitor when the server cannot be reached', () => {
      login.mockReturnValue(throwError(() => ({ status: 0 })));

      signIn.onSubmit();

      expect(signIn.errorMessage()).toBe(NO_CONNECTION_MESSAGE);
    });

    it('uses a plain message for an unexpected failure', () => {
      login.mockReturnValue(throwError(() => ({ status: 500, error: { message: 'Unexpected error occurred' } })));

      signIn.onSubmit();

      expect(signIn.errorMessage()).toBe('Something went wrong. Please try again.');
    });

    it('does not send an empty form', () => {
      signIn.form.setValue({ email: '', password: '' });

      signIn.onSubmit();

      expect(login).not.toHaveBeenCalled();
    });
  });
});
