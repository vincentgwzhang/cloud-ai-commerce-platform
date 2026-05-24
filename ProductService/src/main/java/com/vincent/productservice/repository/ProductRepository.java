package com.vincent.productservice.repository;

import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatusOrderByIdAsc(ProductStatus status);

    List<Product> findByIdInOrderByIdAsc(Iterable<Long> ids);
}
