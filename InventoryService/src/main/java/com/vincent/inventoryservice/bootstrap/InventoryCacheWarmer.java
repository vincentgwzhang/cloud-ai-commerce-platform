package com.vincent.inventoryservice.bootstrap;

import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.repository.InventoryRepository;
import com.vincent.inventoryservice.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/** Preloads hot SKU inventory into Redis at startup. */
@Component
@Profile("!test")
public class InventoryCacheWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryCacheWarmer.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final InventoryProperties properties;

    public InventoryCacheWarmer(
            InventoryRepository inventoryRepository,
            InventoryService inventoryService,
            InventoryProperties properties
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> hotCodes = properties.hotProductCodes();
        if (hotCodes == null || hotCodes.isEmpty()) {
            return;
        }
        int warmed = 0;
        for (String code : hotCodes) {
            inventoryRepository.findByProductCode(code).ifPresent(inventory -> {
                inventoryService.warmCache(inventory);
            });
            warmed++;
        }
        log.info("Warmed {} hot inventory SKUs into Redis", warmed);
    }
}
