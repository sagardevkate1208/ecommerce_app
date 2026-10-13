package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.CategoryRequest;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.entity.Category;
import com.ecommerce.catalog.exception.ApiException;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return toResponse(load(id));
    }

    public CategoryResponse create(CategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "Category already exists");
        }
        Category c = new Category();
        c.setName(name);
        c.setDescription(req.description());
        return toResponse(categories.save(c));
    }

    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = load(id);
        String name = req.name().trim();
        if (!c.getName().equalsIgnoreCase(name) && categories.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "Category already exists");
        }
        c.setName(name);
        c.setDescription(req.description());
        return toResponse(c);
    }

    public void delete(Long id) {
        Category c = load(id);
        if (products.existsByCategoryId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Category still has products");
        }
        categories.delete(c);
    }

    Category load(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
    }
}
