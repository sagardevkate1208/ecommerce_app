package com.ecommerce.order.client;

import com.ecommerce.order.dto.CartInfo;
import com.ecommerce.order.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls cart-service on behalf of the user by forwarding their own Authorization header. */
@Component
public class CartClient {

    private final RestClient restClient;

    public CartClient(RestClient.Builder builder, @Value("${app.cart.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public CartInfo getCart(String authorizationHeader) {
        try {
            return restClient.get().uri("/api/cart")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve().body(CartInfo.class);
        } catch (RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Cart service returned an error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Cart service is unavailable");
        }
    }

    public void clear(String authorizationHeader) {
        try {
            restClient.delete().uri("/api/cart")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Cart service returned an error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Cart service is unavailable");
        }
    }
}
