package com.nextwindow.monitoring.fetch;

import com.nextwindow.source.FetchMethod;
import com.nextwindow.source.MonitoringSource;

/**
 * Retrieves the current content of a source. Implementations must be safe to call concurrently and must
 * never throw for expected failures: they return a failed {@link FetchResult} instead.
 * Authorized API integrations can be added later by implementing this interface for a new {@link FetchMethod}.
 */
public interface SourceFetcher {
    boolean supports(FetchMethod method);

    FetchResult fetch(MonitoringSource source);

    /** Fetches a URL that is not yet registered as a source (used by source analysis). */
    FetchResult fetchUrl(String canonicalUrl);
}
