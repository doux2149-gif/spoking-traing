package com.example.speech.service.iflytek;

public class IatServiceException extends RuntimeException {
    private final int code;

    public IatServiceException(int code, String message) {
        super(message + "（错误码：" + code + "）");
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
