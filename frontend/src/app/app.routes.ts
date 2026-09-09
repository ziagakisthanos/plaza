import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login'
import { ProductList } from './features/products/product-list/product-list';

export const routes: Routes = [
    { path: 'login', component: Login },
    { path: 'products', component: ProductList },

    { path: '', redirectTo: 'products', pathMatch: 'full' }, // default
];
