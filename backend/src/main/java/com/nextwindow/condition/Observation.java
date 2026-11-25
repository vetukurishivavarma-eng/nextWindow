package com.nextwindow.condition;

import com.nextwindow.extraction.Availability;

import java.math.BigDecimal;

/**
 * The current state of a source as seen by conditions.
 *
 * @param textComplete false when {@code text} was truncated (keyword conditions then avoid baselines)
 * @param previousText normalized text of the previous snapshot, if known (used for change summaries)
 */
public record Observation(String title, BigDecimal price, String currency, Availability availability,
                          String text, boolean textComplete, String contentHash, String previousText) {}
