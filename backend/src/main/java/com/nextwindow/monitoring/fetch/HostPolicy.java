package com.nextwindow.monitoring.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Decides which hosts the fetcher may contact and resolves them to the exact addresses it will connect to. */
public interface HostPolicy {
    /** Throws {@link com.nextwindow.source.UrlRejectedException} when the host/port may not be contacted. */
    void validate(String host, int port);

    /** Resolves a host, throwing if any address is not allowed. */
    InetAddress[] resolve(String host) throws UnknownHostException;
}
