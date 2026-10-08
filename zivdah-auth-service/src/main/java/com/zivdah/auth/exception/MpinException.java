package com.zivdah.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// MPIN failures carry their own status (401 wrong/locked MPIN, 403 setup without a recent full
// login), mapped as-is by GlobalExceptionHandler.
@Getter
public class MpinException extends RuntimeException {

    private final HttpStatus status;

    public MpinException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
