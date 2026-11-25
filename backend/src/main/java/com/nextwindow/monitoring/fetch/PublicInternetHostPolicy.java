package com.nextwindow.monitoring.fetch;

import com.nextwindow.source.UrlSafetyValidator;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Production policy: only public internet addresses. */
@Component
public class PublicInternetHostPolicy implements HostPolicy {
    private final UrlSafetyValidator validator;

    public PublicInternetHostPolicy(UrlSafetyValidator validator) {
        this.validator = validator;
    }

    @Override
    public void validate(String host, int port) {
        validator.validateHost(host, port);
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return validator.resolveSafe(host);
    }
}
