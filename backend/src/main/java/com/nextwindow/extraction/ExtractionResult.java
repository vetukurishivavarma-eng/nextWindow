package com.nextwindow.extraction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Normalized facts extracted from a page. Unknown values are null / {@link Availability#UNKNOWN};
 * nothing is guessed. {@code notes} explains uncertain or missing values to the user.
 */
public record ExtractionResult(
        String title,
        BigDecimal price,
        String currency,
        ValueSource priceSource,
        Availability availability,
        ValueSource availabilitySource,
        boolean productDetected,
        String normalizedText,
        String textHash,
        Map<String, String> metadata,
        List<String> notes
) {
    public boolean hasPrice() { return price != null; }
}
