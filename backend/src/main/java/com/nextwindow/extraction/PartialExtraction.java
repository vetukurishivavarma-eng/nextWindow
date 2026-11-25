package com.nextwindow.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Mutable accumulator for one extraction layer. */
final class PartialExtraction {
    String title;
    BigDecimal price;
    String currency;
    Availability availability = Availability.UNKNOWN;
    boolean product;
    final List<String> notes = new ArrayList<>();
}
