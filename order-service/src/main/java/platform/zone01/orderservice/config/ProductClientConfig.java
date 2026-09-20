package platform.zone01.orderservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ProductClientConfig {

    private static final String PRODUCT_SERVICE_URL = "http://product-service";

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient productRestClient(@LoadBalanced RestClient.Builder builder) {
        return builder.baseUrl(PRODUCT_SERVICE_URL).build();
    }
}
