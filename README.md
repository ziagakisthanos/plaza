# Plaza: e-commerce microservices platform

A marketplace built with Spring Boot microservices and an Angular frontend. Sellers list products with images. Clients search and filter them, fill a cart, check out with **pay on delivery**, and follow their orders. Both sides get a profile with their numbers.

## What it does

- **Accounts:** register as a client or a seller, sign in, edit your profile and avatar.
- **Products:** sellers create, edit and delete their own products, with a category and images (JPEG or PNG, up to 2 MB).
- **Search and filters:** search by text, filter by category, price range and stock, sort by newest or price.
- **Cart:** stored on the server, so it survives a refresh, a restart and a new device. Prices and stock are always the current ones.
- **Checkout:** one order per seller, with the prices of that moment. Stock is reserved at checkout.
- **Orders:** buyers and sellers each get an orders page with search and status filter. The seller moves an order through confirmed, shipped and delivered. Either side can cancel a pending or confirmed order (the stock goes back). The buyer can remove a cancelled order or order the same items again.
- **Profile numbers:** buyers see what they spent, what they bought most and where the money went. Sellers see what they earned and their best sellers. Cancelled orders are not counted.

## Architecture

```
                       Angular app  ──HTTP──▶  Gateway (:8080)
                       (:4200)                    │  routes by service name through Eureka
     ┌──────────────┬──────────────┬──────────────┼───────────────┐
     ▼              ▼              ▼              ▼               ▼
User service   Product service  Media service  Order service   Discovery
   :8081          :8082           :8083          :8084         (Eureka :8761)
  userdb        productdb      mediadb+MinIO    orderdb

 Order service ──REST, service token──▶ Product service /internal/products/*  (stock lookup, reserve, release)
 Product service ──Kafka "product-deleted"──▶ Media service                  (cleans up the images)
```

| Module | Role |
|---|---|
| `discovery` | Eureka registry: services find each other by name |
| `gateway` | The only entry point: routing, CORS, TLS when run outside Docker |
| `user-service` | Registration, login, JWT, profile |
| `product-service` | Products, search and filters, the internal stock API |
| `media-service` | Image upload and serving, MIME sniffing, MinIO storage |
| `order-service` | Cart, checkout, orders, profile statistics |
| `common-security` | Shared JWT service and filter |
| `common-web` | Shared error format and base exception handler |
| `frontend` | Angular app |

Each service owns its own MongoDB database and never reads another one.

## Tech stack

Java 21, Spring Boot 3.3, Spring Cloud (Eureka, Gateway), Spring Security, Spring Data MongoDB, Spring Kafka, Maven (multi-module). Angular 21 (standalone components). MongoDB 7, Apache Kafka, MinIO. Docker, Jenkins, SonarQube, Nexus Repository Manager.

## Design decisions

- **The cart lives in the order service**, storing only product ids and quantities. Names, prices and stock are read from the product service each time, so nothing in the cart can be stale.
- **Checkout claims the cart atomically.** Pressing the button twice cannot order the same items twice. If anything fails afterwards, the stock is released and the cart is put back.
- **Stock cannot be oversold.** Each decrement is a single conditional update in the database. A reservation that fails half-way gives back what it took.
- **Orders keep a snapshot** (name and price) of what was bought, so history survives price changes and deleted products.
- **Carts and orders use optimistic locking**, so two requests changing the same cart or order never overwrite each other: the loser retries or gets a clear 409.
- **The stock API is closed twice:** the gateway does not route `/internal/**`, and the product service requires a `SERVICE` role that users can never obtain.
- **Identity comes from the token, never from the request body.** Every service checks the JWT itself and does not rely on the gateway.
- **One error format everywhere:** `{ timestamp, status, message, path, fieldErrors? }`, produced by `common-web`. Unknown URLs, bad JSON, wrong methods and oversized uploads answer with the right 4xx, never a 500.

## Run it

Requirements: JDK 21, Docker with Compose. (Node.js is only needed to work on the frontend.)

```bash
cp .env.example .env            # set JWT_SECRET, e.g. openssl rand -base64 32
sh ./mvnw clean package -DskipTests   # the service images copy the built jars
docker compose up --build -d
```

- App: http://localhost:4200
- API through the gateway: http://localhost:8080
- Service registry: http://localhost:8761
- MinIO console: http://localhost:9001 (`minioadmin` / `minioadmin`)

To try it: register a seller and add a product with an image, register a client in another browser profile, add the product to the cart and check out, then confirm and ship the order as the seller and look at both profiles.

Stop everything with `docker compose down` (add `-v` to also delete the data).

Inside Docker the gateway serves plain HTTP on 8080, which is what the frontend uses. Run outside Docker, it serves HTTPS on 8443 with a self-signed certificate that you create once in `gateway/src/main/resources`:

```bash
keytool -genkeypair -alias buy01 -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore keystore.p12 -validity 365 -dname "CN=localhost" -storepass changeit
```

## Test it

```bash
sh ./mvnw clean verify                       # every Java module, with JaCoCo coverage reports
cd frontend && npm ci && npm test            # Vitest (Node 20; on newer Node prefix with NODE_OPTIONS=--no-experimental-webstorage)
```

The backend tests cover the services, the security rules for every role (with real signed tokens) and the error responses. The frontend tests cover the services, the components and the interceptors.

## API overview

Everything goes through the gateway. `CLIENT` and `SELLER` are the two roles.

| Method and path | Access | Description |
|---|---|---|
| `POST /auth/register`, `POST /auth/login` | public | Create an account, get a JWT |
| `GET /users/me`, `PUT /users/me` | signed in | Own profile |
| `GET /products` | public | Search: `q`, `category`, `minPrice`, `maxPrice`, `inStock`, `sort` (`newest`, `price_asc`, `price_desc`) |
| `GET /products/categories`, `GET /products/{id}` | public | Categories, one product |
| `POST /products`, `PUT` and `DELETE /products/{id}` | seller (owner) | Manage products |
| `POST /media/images`, `DELETE /media/images/{id}` | seller (owner) | Upload (multipart, 2 MB, sniffed JPEG or PNG), delete |
| `GET /media/images/{id}`, `GET /media/product/{productId}` | public | Serve an image, list a product's images |
| `GET /cart`, `DELETE /cart` | client | Read or empty the cart |
| `POST /cart/items`, `PUT` and `DELETE /cart/items/{productId}` | client | Add, set the quantity, remove |
| `POST /orders/checkout` | client | `{ paymentMethod: "PAY_ON_DELIVERY", deliveryAddress }`, returns one order per seller |
| `GET /orders?q&status` | client | Own orders |
| `GET /orders/seller?q&status` | seller | Orders received |
| `GET /orders/{id}` | buyer or seller of it | One order |
| `PUT /orders/{id}/status` | seller of it | Next step only: confirmed, shipped, delivered |
| `PUT /orders/{id}/cancel` | buyer or seller | Pending or confirmed orders, gives the stock back |
| `DELETE /orders/{id}` | buyer | Cancelled orders only |
| `POST /orders/{id}/redo` | buyer | Order the same items again (delivered or cancelled orders) |
| `GET /orders/stats/client`, `GET /orders/stats/seller` | client, seller | Profile numbers |

## Security

- Passwords are hashed with BCrypt and never returned.
- JWTs are signed and carry the user id and role. Role rules and ownership rules are enforced on the server; the frontend guards only shape the interface.
- The same error for a wrong email and a wrong password, so accounts cannot be guessed.
- Uploads are checked by their content (magic bytes), not the name or the declared type, and limited to 2 MB.
- 401 and 403 answers use the same error format as every other error.

## CI/CD and code quality

Jenkins builds, tests and deploys the project, and SonarQube checks the quality gate. The pipeline is the root `Jenkinsfile`.

| Stage | Branches | `main` |
|---|---|---|
| Build, unit tests, JaCoCo | yes | yes |
| SonarQube analysis and quality gate (fails the build) | | yes |
| Publish artifacts and images to Nexus | | yes |
| Build images, deploy, smoke check | | yes |

- A failed deploy rolls back to the previous images. Nothing is left running after a build.
- Jenkins polls every two minutes and runs every night. Secrets come from Jenkins credentials (`jwt-secret`, `sonar-token`, `nexus-credentials`).
- Start the tools with `docker compose -f docker-compose.sonar.yml up -d` (needs `SONAR_DB_PASSWORD` in `.env`) and then `docker compose -f docker-compose.jenkins.yml up -d --build`: Jenkins joins the Docker network that the SonarQube stack creates. Jenkins is on http://localhost:8090 and SonarQube on http://localhost:9100. Create a Pipeline job from SCM that builds `*/main` and `*/feature/**` with the script path `Jenkinsfile`, and a SonarQube project with the key `buy-02`.
- How the work is reviewed and what the gate has improved: [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/sonarqube.md](docs/sonarqube.md).

## Artifact management (Nexus)

Every build on `main` also publishes its own output to a private Nexus Repository Manager
instance instead of leaving it inside the disposable build workspace:

```bash
cp .env.example .env                                # set NEXUS_ADMIN_PASSWORD, NEXUS_USER, NEXUS_PASSWORD
docker compose -f docker-compose.nexus.yml up -d
sh scripts/nexus-setup.sh                            # creates the repositories, idempotent
./mvnw -s ci/settings.xml deploy -DskipTests          # jars to jars-releases / jars-snapshots
sh scripts/publish-images.sh                          # images to docker-hosted
```

- Nexus UI: http://localhost:8091. Docker registry: `localhost:5000`.
- Dependencies resolve through Nexus's `maven-central` proxy instead of hitting Maven Central directly.
- Releases (`jars-releases`, images tagged with a plain version) cannot be redeployed once published; `main` keeps building `-SNAPSHOT`s.
- Full setup, the repository layout, and how versions are cut and retrieved: [docs/nexus.md](docs/nexus.md).

## Known trade-offs

- Cancelling an order gives its stock back in a second step. If the product service is down at that moment the order is still cancelled and the stock has to be released by hand.
- JWTs cannot be revoked; they expire after one hour. A production system would add refresh tokens.
- Money is stored as a `double`, rounded to two decimals in one place. `BigDecimal` would be the next step.
- Order lists are not paginated, and their search runs over the orders of the signed-in user only.
- The delivery address is one free-text field: the buyer is asked to include a name and a phone number.
- The frontend is analysed by SonarQube but not measured for coverage, because the Jenkins image has no Node.js.
- Media and product deletes are not transactional across MinIO and MongoDB; a periodic clean-up would remove orphaned files.
