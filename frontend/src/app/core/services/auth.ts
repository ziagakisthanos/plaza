import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { jwtDecode } from 'jwt-decode';
import { environment } from '../../../environments/environment';

interface LoginRequest { email: string; password: string; }
interface RegisterRequest { name: string; email: string; password: string; role: string; }
interface AuthResponse { token: string; }
interface JwtPayload { sub: string; role: string; exp: number; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;
  private readonly TOKEN_KEY = 'auth_token';

  // --- LOGIN ---
  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, credentials)
      .pipe(
        tap(response => this.storeToken(response.token))
      );
  }

  // --- REGISTER ---
  register(data: RegisterRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/auth/register`, data);
  }

  // --- LOGOUT ---
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }

  // --- TOKEN ACCESS ---
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private storeToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  // --- AUTH STATE ---
  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) return false;
    const payload = this.decodeToken(token);
    if (!payload) return false;
    return payload.exp * 1000 > Date.now();
  }

  getRole(): string | null {
    const token = this.getToken();
    if (!token) return null;
    return this.decodeToken(token)?.role ?? null;
  }

  getUserId(): string | null {
    const token = this.getToken();
    if (!token) return null;
    return this.decodeToken(token)?.sub ?? null;
  }

  private decodeToken(token: string): JwtPayload | null {
    try {
      return jwtDecode<JwtPayload>(token);
    } catch {
      return null;   // malformed token
    }
  }
}