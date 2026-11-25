package com.nextwindow.extraction;

public enum Availability {
    IN_STOCK, OUT_OF_STOCK, PREORDER, BACKORDER, LIMITED, DISCONTINUED, UNKNOWN;

    public boolean isKnown() { return this != UNKNOWN; }

    /** True when an order can be placed right now. */
    public boolean isPurchasable() { return this == IN_STOCK || this == LIMITED; }
}
