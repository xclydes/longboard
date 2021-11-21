package com.xclydes.finance.longboard.upwork;

import lombok.Getter;

import java.text.MessageFormat;

@Getter
public class APIException extends RuntimeException {

    private final String reason;
    private final Integer code;

    public APIException(final String message, final String reason, final Integer code) {
        super(message);
        this.reason = reason;
        this.code = code;
    }

    @Override
    public String toString() {
        return MessageFormat.format("[Upwork] message='{0}', reason='{1}', code={2}", this.getMessage(), reason, code);
    }
}
