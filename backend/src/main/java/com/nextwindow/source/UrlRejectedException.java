package com.nextwindow.source;

import com.nextwindow.common.ApiException;
import org.springframework.http.HttpStatus;

public class UrlRejectedException extends ApiException {
    public UrlRejectedException(String message) {
        super(HttpStatus.BAD_REQUEST, "URL_REJECTED", message);
    }
}
