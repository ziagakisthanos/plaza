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
 
