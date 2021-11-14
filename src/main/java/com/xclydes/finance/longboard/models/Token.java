package com.xclydes.finance.longboard.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.io.Serializable;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Setter
@ToString
public class Token implements Serializable {

    public static final String CtxKey = "com.xclydes.finance.longboard.models.Token";
    public final static Token EMPTY = Token.of("");

    private final String key;
    private final String secret;
    private final Integer expiry;

    public boolean hasKey() {
        return StringUtils.hasText(StringUtils.trimWhitespace(this.getKey()));
    }

    public boolean hasSecret() {
        return StringUtils.hasText(StringUtils.trimWhitespace(this.getSecret()));
    }

    public boolean hasContent() {
        return this.hasKey() || this.hasSecret();
    }

    @JsonIgnore
    public boolean isComplete() {
        return this.hasKey() && this.hasSecret();
    }

    @NotNull
    @Contract("_ -> new")
    public static Token of(final String key) {
        return Token.of(key, null);
    }

    @NotNull
    @Contract("_, _ -> new")
    public static Token of(final String key, final String secret) {
        return Token.of(key, secret, null);
    }

    @NotNull
    @Contract("_, _, _ -> new")
    public static Token of(final String key, final String secret, final Integer expiry) {
        return new Token(key, secret, expiry);
    }
}
