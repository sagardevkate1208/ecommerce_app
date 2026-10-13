package com.ecommerce.cart.client;

import com.ecommerce.cart.dto.ProductInfo;
import com.ecommerce.cart.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls catalog-service's public product endpoint. */
@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder, @Value("${app.catalog.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** Returns the product, or throws 404 if it does not exist or is inactive, 503 if Catalog is unreachable. */
    public ProductInfo getProduct(Long productId) {
        try {
            return restClient.get().uri("/api/products/{id}", productId).retrieve().body(ProductInfo.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Catalog service returned an error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service is unavailable");
        }
    }
}
