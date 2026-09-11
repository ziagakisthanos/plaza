# buy-01 — E-Commerce Microservices Platform

A full-stack e-commerce platform built with **Spring Boot microservices** (backend) and
**Angular** (frontend). Users register as clients (browse) or sellers (manage products and
images). Demonstrates service decomposition, synchronous and asynchronous inter-service
communication, JWT-based security with role and ownership enforcement, secure file uploads,
and object storage.

---

## Architecture

```
                         ┌─────────────┐
   Angular SPA  ──HTTPS──▶│   Gateway   │  (single entry point, JWT-aware, CORS, routing)
   (localhost:4200)       │  :8443/:8080│
                          └──────┬──────┘
                                 │  routes by service name via Eureka
          ┌──────────────────────┼──────────────────────┐
          ▼                      ▼                       ▼
   ┌─────────────┐        ┌─────────────┐         ┌─────────────┐
   │ User Service│        │Product Svc  │         │ Media Service│
   │   :8081     │        │   :8082     │         │    :8083     │
   │ auth, JWT,  │        │ CRUD, owner-│         │ upload/serve │
   │ profiles    │        │ ship checks │         │ validation   │
   └──────┬──────┘        └──────┬──────┘         └──────┬───────┘
          │ userdb               │ productdb             │ mediadb + MinIO
          ▼                      ▼                       ▼
   ┌───────────────────────────────────────────────────────────┐
   │              MongoDB (database per service)                │
   └───────────────────────────────────────────────────────────┘

   Product ──"PRODUCT_DELETED" event──▶ Kafka ──▶ Media (cleans up orphaned images)

   Discovery (Eureka, :8761) — service registry all services register with
```

### Services
- **Discovery (Eureka, 8761)** — service registry; services find each other by name, not address.
- **Gateway (8080 / 8443 HTTPS)** — single front door; routes by service name, terminates TLS,
  applies CORS.
- **User Service (8081)** — registration, login, JWT issuance, profiles (`/me`), roles.
- **Product Service (8082)** — product CRUD; public reads, seller-only writes, ownership checks.
- **Media Service (8083)** — image upload/serve/delete; MIME + size validation; MinIO storage.
- **common-security** — shared library (`JwtService`, `JwtAuthFilter`) imported by all services.

---

## Tech Stack

**Backend:** Java 21, Spring Boot 3.3, Spring Cloud (Eureka, Gateway), Spring Security,
Spring Data MongoDB, Spring Kafka, jjwt, MinIO client, Maven (multi-module).
**Frontend:** Angular 21 (standalone components), Angular Material, RxJS, TypeScript.
**Infrastructure:** MongoDB, MinIO (S3-compatible object storage), Apache Kafka (KRaft),
all via Docker.

---

## Key Design Decisions

- **Database per service** — each service owns its own MongoDB database; the boundary is
  enforced by giving each service only its own connection string. No service reads another's data.
- **Object storage for images** — image *bytes* live in MinIO; only *metadata* lives in Mongo.
  Databases aren't for blobs, and a shared object store works across multiple stateless instances.
- **Stateless JWT auth** — the server keeps no session; each request carries a signed token
  re-verified with a shared secret. Any service instance can verify any token. Trade-off:
  no instant revocation, mitigated with short (1h) expiry.
- **Self-securing services (zero-trust)** — each service validates the JWT itself via the shared
  `common-security` library, rather than trusting the gateway. A service is secure regardless of
  how a request reaches it.
- **Identity from the token, never the body** — ownership (`userId`) is always taken from the
  authenticated caller's token, never accepted from the request, preventing impersonation.
- **Sync vs async communication** — reads, auth, and uploads are synchronous (the caller needs a
  response). Product deletion publishes an async `PRODUCT_DELETED` event so Media can clean up
  orphaned images without the user waiting and without coupling the two services.
- **Secure uploads** — image type is verified by **sniffing file content** (magic bytes), not the
  client-supplied content-type; size capped at 2 MB at both framework and application level.

---

## Prerequisites

- Java 21, Maven
- Node.js 20+ and Angular CLI (`npm install -g @angular/cli`)
- Docker + Docker Compose

---

## Running the Project

### 1. Start infrastructure (MongoDB, MinIO, Kafka)
From the project root:
```bash
docker compose up -d
```
Verify: `docker ps` shows `buy01-mongo`, `buy01-minio`, `buy01-kafka`.

- MinIO console: http://localhost:9001 (user/pass: `minioadmin` / `minioadmin`)
- The `product-images` bucket is auto-created by Media Service on startup.

### 2. Set the JWT secret
Each service reads `JWT_SECRET` (must be identical across services). Set it as an environment
variable, or rely on the dev default in each service's `application.yml`.

### 3. Start the backend services (in order)
Run from your IDE or with Maven, starting Discovery first:
1. `discovery`   (8761)
2. `gateway`     (8443 HTTPS)
3. `user-service`, `product-service`, `media-service`

Confirm all services register at the Eureka dashboard: http://localhost:8761

### 4. Start the frontend
```bash
cd frontend
npm install
ng serve --ssl
```
Open **https://localhost:4200**.

> **Self-signed certificates:** both the frontend and gateway use self-signed certs, so the
> browser will warn that the connection is not trusted. Accept both (visit
> `https://localhost:8443/products` once directly to trust the gateway cert), or enable
> `chrome://flags/#allow-insecure-localhost`. In production these would be CA-issued certs.

---

## HTTPS / TLS

TLS terminates at the **gateway** (self-signed cert via a PKCS12 keystore); the frontend is
also served over HTTPS. Internal service-to-service traffic is HTTP over the trusted network —
the standard edge-termination pattern. Regenerate the gateway keystore with:
```bash
keytool -genkeypair -alias buy01 -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore keystore.p12 -validity 365 -dname "CN=localhost" -storepass changeit
```

---

## API Overview

All requests go through the gateway (`https://localhost:8443`).

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/auth/register` | Public | Register (role: CLIENT or SELLER) |
| POST | `/auth/login` | Public | Log in → returns JWT |
| GET  | `/users/me` | Authenticated | Current user's profile |
| PUT  | `/users/me` | Authenticated | Update name / avatar |
| GET  | `/products` | Public | List all products |
| GET  | `/products/{id}` | Public | Get one product |
| POST | `/products` | SELLER | Create a product |
| PUT  | `/products/{id}` | SELLER (owner) | Update own product |
| DELETE | `/products/{id}` | SELLER (owner) | Delete own product |
| POST | `/media/images` | SELLER | Upload an image (multipart, ≤2MB, image/*) |
| GET  | `/media/images/{id}` | Public | Serve an image |
| GET  | `/media/product/{productId}` | Public | List a product's images |
| DELETE | `/media/images/{id}` | SELLER (owner) | Delete an image |

Every service exposes `/actuator/health`.

---

## Frontend Features

- Sign-in / sign-up with role selection (reactive forms, inline validation)
- Public product listing with images (Material card grid)
- Seller dashboard: create / edit / delete products, upload images
- Profile page with randomizable preset SVG avatars
- Route guards (auth + role) and an HTTP interceptor that attaches the JWT and handles 401/403
- **Note:** frontend guards are UX only; authorization is enforced server-side.

---

## Security Summary

- Passwords hashed with **BCrypt**; never returned in any response.
- **JWT** signed with a secret; role and user id carried as claims.
- **Role-based** access (public reads, seller-only writes) and **ownership** enforcement
  (only the creating seller edits/deletes a product or its media).
- Identical error responses for bad email vs. bad password (**no user enumeration**).
- Uploads validated by **content sniffing** and size limits.
- Consistent structured error responses (`ErrorResponseDTO`) across all services, including
  security-layer 401/403.

---

## Known Trade-offs / Future Work

- Error-handling boilerplate is duplicated across services (candidate for a `common-web` module).
- No cart/checkout — CLIENT is browse-only by design (commerce is out of scope).
- JWTs are non-revocable by design; production would add refresh tokens or a blocklist.
- Cross-store deletes (MinIO + Mongo) aren't transactional; a reconciliation sweep would handle
  rare orphaned objects in production.