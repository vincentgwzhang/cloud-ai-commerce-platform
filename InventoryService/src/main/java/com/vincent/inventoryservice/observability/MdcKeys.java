package com.vincent.inventoryservice.observability;

public final class MdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String TRACE_ID = "traceId";
    public static final String ORDER_NO = "orderNo";
    public static final String PRODUCT_CODE = "productCode";
    public static final String EVENT_ID = "eventId";

    private MdcKeys() {
    }
}
