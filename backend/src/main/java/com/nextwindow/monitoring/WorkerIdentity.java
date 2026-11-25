package com.nextwindow.monitoring;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

/** Unique id of this application instance, recorded on source leases. */
@Component
public class WorkerIdentity {
    private final String id;

    public WorkerIdentity() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "worker";
        }
        String raw = host + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.id = raw.length() > 100 ? raw.substring(raw.length() - 100) : raw;
    }

    public String id() { return id; }
}
