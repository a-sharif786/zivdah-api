package com.zivdah.common.upload;

public class UploadFailedException extends RuntimeException {
    public UploadFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
