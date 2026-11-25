package com.nextwindow.condition;

import com.nextwindow.watch.Watch;
import com.nextwindow.watch.WatchType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ConditionEngine {
    private final Map<WatchType, ConditionEvaluator> evaluators = new EnumMap<>(WatchType.class);

    public ConditionEngine(List<ConditionEvaluator> list) {
        list.forEach(e -> evaluators.put(e.type(), e));
        for (WatchType t : WatchType.values()) {
            if (!evaluators.containsKey(t)) throw new IllegalStateException("Missing evaluator for " + t);
        }
    }

    public Evaluation evaluate(Watch watch, Observation observation) {
        return evaluators.get(watch.getWatchType()).evaluate(WatchCriteria.of(watch), watch.getLastObservedValue(), observation);
    }

    /** Computes the baseline state for a new or reconfigured watch without ever triggering. */
    public String baseline(Watch watch, Observation observation) {
        return evaluators.get(watch.getWatchType()).evaluate(WatchCriteria.of(watch), null, observation).observed();
    }
}
