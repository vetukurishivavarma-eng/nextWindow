package com.nextwindow.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {
    private ClientIp() {}

    /** Uses the servlet remote address; configure server.forward-headers-strategy behind a trusted proxy. */
    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
