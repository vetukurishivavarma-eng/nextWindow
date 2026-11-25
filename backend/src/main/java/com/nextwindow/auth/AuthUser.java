package com.nextwindow.auth;

/** Authenticated principal stored in the security context. Never carries credentials. */
public record AuthUser(Long id, String email, Long sessionId) {}
