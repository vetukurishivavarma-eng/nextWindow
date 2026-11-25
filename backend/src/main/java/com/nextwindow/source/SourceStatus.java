package com.nextwindow.source;

public enum SourceStatus {
    /** Registered, no successful check yet. */
    PENDING,
    /** Last check succeeded. */
    ACTIVE,
    /** Recent checks failed; retrying with backoff. */
    DEGRADED,
    /** Many consecutive failures; retrying rarely. */
    FAILING,
    /** Access refused by the site (robots.txt, 401/403). We never bypass this; retried rarely. */
    BLOCKED,
    /** Permanently disabled by an operator. */
    DISABLED
}
