package com.nextwindow.condition;

import com.nextwindow.watch.WatchType;

/**
 * Pure, deterministic evaluation of one watch type. Implementations must treat a null {@code previous}
 * as "no baseline yet" and never trigger in that case.
 */
public interface ConditionEvaluator {
    WatchType type();

    Evaluation evaluate(WatchCriteria criteria, String previous, Observation now);
}
