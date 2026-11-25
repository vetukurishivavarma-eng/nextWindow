package com.nextwindow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextwindow.common.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF defence for the cookie-authenticated JSON API: state-changing requests must carry a custom
 * header, which cross-site forms cannot set and cross-origin scripts can only set after a CORS preflight
 * that our allow-list rejects. Combined with SameSite cookies this blocks cross-site request forgery.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Requested-With";
    public static final String VALUE = "NextWindow";
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final ObjectMapper mapper;

    public CsrfHeaderFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE.contains(request.getMethod()) && request.getRequestURI().startsWith("/api/")
                && !VALUE.equals(request.getHeader(HEADER))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.body(HttpStatus.FORBIDDEN,
                    "CSRF_REJECTED", "Request rejected: missing client header.", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}
