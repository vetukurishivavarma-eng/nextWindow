package com.nextwindow.condition;

import com.nextwindow.watch.Watch;
import com.nextwindow.watch.WatchType;

import java.math.BigDecimal;

/** The user-configured part of a watch, decoupled from the entity for pure evaluation. */
public record WatchCriteria(WatchType type, String title, BigDecimal targetPrice, String keyword) {
    public static WatchCriteria of(Watch w) {
        return new WatchCriteria(w.getWatchType(), w.getTitle(), w.getTargetPrice(), w.getTargetKeyword());
    }
}
