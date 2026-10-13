package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductRequest;
import com.ecommerce.catalog.dto.ProductResponse;
import com.ecommerce.catalog.entity.Category;
import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.exception.ApiException;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

    private static final Set<String> SORTABLE = Set.of("name", "price", "createdAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    /** Public search: only active products. All filters are optional. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                                int page, int size, String sortBy, String direction) {
        return find(q, categoryId, minPrice, maxPrice, Boolean.TRUE, page, size, sortBy, direction);
    }

    /** Admin search: active and inactive products. active = null means both. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> adminSearch(String q, Long categoryId, Boolean active,
                                                     int page, int size, String sortBy, String direction) {
        return find(q, categoryId, null, null, active, page, size, sortBy, direction);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(products.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found")));
    }

    /** Admin read: also returns inactive products so they can be edited and re-activated. */
    @Transactional(readOnly = true)
    public ProductResponse adminGet(Long id) {
        return toResponse(products.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found")));
    }

    public ProductResponse create(ProductRequest req) {
        String sku = req.sku().trim();
        if (products.existsBySkuIgnoreCase(sku)) {
            throw new ApiException(HttpStatus.CONFLICT, "SKU already exists");
        }
        Product p = new Product();
        apply(p, req, sku);
        return toResponse(products.save(p));
    }

    public ProductResponse update(Long id, ProductRequest req) {
        Product p = products.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        String sku = req.sku().trim();
        if (!p.getSku().equalsIgnoreCase(sku) && products.existsBySkuIgnoreCase(sku)) {
            throw new ApiException(HttpStatus.CONFLICT, "SKU already exists");
        }
        apply(p, req, sku);
        return toResponse(p);
    }

    /** Soft delete: the row stays (orders may reference it) but disappears from the public catalog. */
    public void deactivate(Long id) {
        Product p = products.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        p.setActive(false);
        p.touch();
    }

    private PageResponse<ProductResponse> find(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                               Boolean active, int page, int size, String sortBy, String direction) {
        if (!SORTABLE.contains(sortBy)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "sortBy must be one of " + SORTABLE);
        }
        Sort sort = Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);

        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("sku")), like)));
            }
            if (categoryId != null) {
                p.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (minPrice != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                p.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        Page<Product> result = products.findAll(spec, pageable);
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private void apply(Product p, ProductRequest req, String sku) {
        Category category = categories.findById(req.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setSku(sku);
        p.setPrice(req.price());
        p.setCategory(category);
        p.setImageUrl(req.imageUrl());
        p.setActive(req.active() == null || req.active());
        p.touch();
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getSku(), p.getPrice(),
                p.getCategory().getId(), p.getCategory().getName(), p.getImageUrl(), p.isActive(), p.getCreatedAt());
    }
}
