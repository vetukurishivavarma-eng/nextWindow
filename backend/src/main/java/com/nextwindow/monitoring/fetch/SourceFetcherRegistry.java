package com.nextwindow.monitoring.fetch;

import com.nextwindow.source.FetchMethod;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceFetcherRegistry {
    private final List<SourceFetcher> fetchers;

    public SourceFetcherRegistry(List<SourceFetcher> fetchers) {
        this.fetchers = fetchers;
    }

    public SourceFetcher forMethod(FetchMethod method) {
        return fetchers.stream().filter(f -> f.supports(method)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No fetcher available for " + method));
    }

    public boolean isSupported(FetchMethod method) {
        return fetchers.stream().anyMatch(f -> f.supports(method));
    }
}
