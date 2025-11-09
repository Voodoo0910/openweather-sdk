package org.sdk;

public class SdkException extends RuntimeException {

    public SdkException(String msg) {
        super(msg);
    }

    public SdkException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
