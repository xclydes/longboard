package com.xclydes.finance.longboard.models;

public class RequestToken extends Token {

    public final String url;

    public RequestToken(final String key, final String secret, final String url) {
        super(key, secret, null);
        this.url = url;
    }
}
