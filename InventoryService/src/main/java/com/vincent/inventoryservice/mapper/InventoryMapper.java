package com.vincent.inventoryservice.mapper;

import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.entity.Inventory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    InventoryResponse toResponse(Inventory inventory);
}
