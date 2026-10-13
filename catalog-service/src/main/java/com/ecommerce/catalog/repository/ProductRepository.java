package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsByCategoryId(Long categoryId);

    Optional<Product> findByIdAndActiveTrue(Long id);
}
