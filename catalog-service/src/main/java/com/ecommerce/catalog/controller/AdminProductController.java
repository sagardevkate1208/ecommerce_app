package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductResponse;
import com.ecommerce.catalog.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only reads (enforced in SecurityConfig: anything outside the public GETs needs ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    /** Like the public search, but includes inactive products. active=true|false filters; omit for both. */
    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return productService.adminSearch(q, categoryId, active, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.adminGet(id);
    }
}
