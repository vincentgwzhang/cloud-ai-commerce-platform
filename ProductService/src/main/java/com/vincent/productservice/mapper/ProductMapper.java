package com.vincent.productservice.mapper;

import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponses(List<Product> products);
}
