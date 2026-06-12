package com.configcat;

import java.util.Map;

/**
 * Represents the result of a forceRefresh() call.
 */
public class RefreshResult {
    private final boolean success;
    private final Object error;
    private final RefreshErrorCode errorCode;

    RefreshResult(boolean success, Object error, RefreshErrorCode errorCode) {
        this.success = success;
        this.error = error;
        this.errorCode = errorCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public String error() {
        if(error !=  null) {
            return error.toString();
        }
        return null;
    }

    public RefreshErrorCode errorCode() {
        return errorCode;
    }
}
