package com.vincent.orderservice.observability;

import org.slf4j.Logger;

/**
 * Consistent {@code business_event} log lines for grep-friendly operations dashboards.
 */
public final class BusinessEventLog {

    private BusinessEventLog() {
    }

    public static void info(
            Logger log,
            String eventType,
            String orderNo,
            String productCode,
            String eventId
    ) {
        try {
            MdcSupport.putBusinessContext(orderNo, productCode, eventId);
            log.info("business_event eventType={} orderNo={} productCode={} eventId={}",
                    eventType, orderNo, productCode, eventId);
        } finally {
            MdcSupport.clearBusinessContext();
        }
    }
}
