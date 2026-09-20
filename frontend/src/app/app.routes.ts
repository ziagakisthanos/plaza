import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { ProductList } from './features/products/product-list/product-list';
import { SellerDashboard } from './features/dashboard/seller-dashboard/seller-dashboard';
import { authGuard } from './core/guards/auth-guard';
import { roleGuard } from './core/guards/role-guard';
import { clientGuard } from './core/guards/client-guard';
import { CartPage } from './features/cart/cart/cart';
import { Profile } from './features/profile/profile/profile';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'products', component: ProductList },
  {
    path: 'dashboard',
    component: SellerDashboard,
    canActivate: [authGuard, roleGuard],
  },
  { path: 'profile', component: Profile, canActivate: [authGuard] },
  { path: 'cart', component: CartPage, canActivate: [authGuard, clientGuard] },

  { path: '', redirectTo: 'products', pathMatch: 'full' }, // default
];
