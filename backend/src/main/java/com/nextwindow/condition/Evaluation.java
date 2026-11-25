package com.nextwindow.condition;

/**
 * Result of evaluating one watch.
 *
 * @param observed new state to remember for the watch; null means "keep the previous state" (value unknown)
 * @param trigger  non-null when an alert should be raised
 */
public record Evaluation(String observed, Trigger trigger) {
    public static Evaluation keep() { return new Evaluation(null, null); }

    public static Evaluation state(String observed) { return new Evaluation(observed, null); }

    public static Evaluation fire(String observed, Trigger trigger) { return new Evaluation(observed, trigger); }

    public boolean triggered() { return trigger != null; }
}
