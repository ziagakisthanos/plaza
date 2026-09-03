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



