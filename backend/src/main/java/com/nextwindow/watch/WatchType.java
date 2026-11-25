package com.nextwindow.watch;

public enum WatchType {
    ANY_CHANGE, PRICE_DROP, PRICE_BELOW, STOCK_AVAILABLE, KEYWORD_FOUND, KEYWORD_REMOVED, STATUS_CHANGE;

    public boolean needsKeyword() { return this == KEYWORD_FOUND || this == KEYWORD_REMOVED; }

    public boolean needsTargetPrice() { return this == PRICE_BELOW; }

    public boolean needsPrice() { return this == PRICE_DROP || this == PRICE_BELOW; }

    public boolean needsAvailability() { return this == STOCK_AVAILABLE || this == STATUS_CHANGE; }
}
