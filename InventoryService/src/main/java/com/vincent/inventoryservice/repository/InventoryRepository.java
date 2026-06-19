package com.vincent.inventoryservice.repository;

import com.vincent.inventoryservice.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductCode(String productCode);

    /**
     * 如果 Inventory 没有 @version 字段也是不可以的。 基本没法发挥乐观锁作用。
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select i from Inventory i where i.productCode = :productCode")
    Optional<Inventory> findByProductCodeForUpdate(@Param("productCode") String productCode);
}
