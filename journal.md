buy-01 is the parent of the multi-modular architecture 
we are building with microservices

### 5 services
- Discovery
- Gateway
- User
- Product
- Media

### DISCOVERY
#### ```PORT: 8761```
The Eureka server. The front desk. 
It's a running program whose entire job is to hold a live list: service name → current address.


### GATEWAY
#### ```PORT: 8080```
The single front door where Angular will eventually talk to, which routes incoming requests to the right service and (later) checks auth, handles CORS, etc.


### MONGODB via docker compose
#### ```MONGODB AT :27017```
docker compose.yml file for mongo db configs without having to download any dependencies


### USER
#### ```PORT: 8081```
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


### genereate random secret key 
```openssl rand -base64 32```