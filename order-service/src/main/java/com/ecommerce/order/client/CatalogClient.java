package com.ecommerce.order.client;

import com.ecommerce.order.dto.ProductInfo;
import com.ecommerce.order.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls catalog-service's public product endpoint to get the live price at checkout. */
@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder, @Value("${app.catalog.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** Returns the product, or throws 409 if it is gone or inactive, 503 if Catalog is unreachable. */
    public ProductInfo getProduct(Long productId) {
        try {
            return restClient.get().uri("/api/products/{id}", productId).retrieve().body(ProductInfo.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Product " + productId + " is no longer available; remove it from your cart");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Catalog service returned an error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service is unavailable");
        }
    }
}
