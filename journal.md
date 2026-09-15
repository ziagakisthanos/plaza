buy-01 is the parent of the multi-modular architecture 
we are building with microservices
## 1 Library

- common-security

extracting all jwt logic out of service into a new library so all services can reuse them. Each
service keeps its own SecurityConfig for service-specific route rules.


## 5 Services
- Discovery
- Gateway
- User
- Product
- Media

### DISCOVERY ```PORT: 8761```
The Eureka server. The front desk. 
It's a running program whose entire job is to hold a live list: service name → current address.


### GATEWAY  ```PORT: 8080```
The single front door where Angular will eventually talk to, which routes incoming requests to the right service and (later) checks auth, handles CORS, etc.


#### MONGODB via docker compose ```port :27017```
docker compose.yml file for mongo db configs without having to download any dependencies

### MONGODB SHELL
get into the shell
- ```docker exec -it buy01-mongo mongosh```

see all databases
- ```show dbs```

switch into a database
- ```use userdb```

list tables (while in 'use userdb')
- ```show collections```

show records 
- ```db.users.find().pretty()```


how many products 
- ```db.products.countDocuments()```

all products owned by one seller 
- ```db.products.find({ userId: "paste-a-seller-id" }) ```

only sellers 
- ```db.users.find({ role: "SELLER" }).pretty()```


### USER ```PORT: 8081```
where we handle user and auth requests.



#### ```@Slf4j```Annotation is Lombok's logger.
In order to handle exception throwing and error handling differently on server side and client side.
Throw a vague 500 error on client side while explicitly describing the error message server side using lomboks logger. 

### SPRING AUTH TOKEN
Spring Security represents "who is the authenticated user for this request" with an 
Authentication object stored in the SecurityContext.
UsernamePasswordAuthenticationToken is just the standard implementation of that.
The name is misleading because we're not doing username/password auth here; we already authenticated via JWT.
We're just using this class as the container to tell Spring "this request belongs to this user with these roles."


#### genereate random secret key ```openssl rand -base64 32```

 ```@AuthenticationPrincipal ``` the controller pulls the authenticated caller's id out of the security context 
 (JwtAuthFilter is set as the principal) and passes it into the 
 service as a separate argument (it does not come from the request body).
 
## Kafka

After adding the kafka dependency in the services we need events to watch
Kafka is then managed by Spring Boot BOM so no version is needed.

**Kafka Spring Support annotations**
- **KafkaTemplate** (for producing)
- **KafkaListener** (for consuming)


### HTTPS end to end

- generate a public or private key par and wrap it in a self-signed cert.
```
keytool -genkeypair -alias buy01 -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 365 -dname "CN=localhost, OU=dev, O=buy01, L=City, ST=State, C=GR" -storepass changeit 
 ```

- change SSL on the gateway .yml file to serve HTTPS port 8443 and add the cert

### Build dockerized App
```
docker compose up --build -d
```

## Jenkins ```PORT: 8090```

run to get Jenkins container
```
docker compose -f docker-compose.jenkins.yml up -d
```
extract the initial jenkins admin password
```
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```
- search and get the correct docker group id if need ```stat -c '%g' /var/run/docker.sock```

#### In Jenkins dashboard

Manage Jenkins → Credentials → Add Credentials
Kind: Secret text, Secret: jwt secret