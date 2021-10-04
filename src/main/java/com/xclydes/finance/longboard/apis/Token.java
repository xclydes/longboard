package com.xclydes.finance.longboard.apis;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class Token {

    public final static Token EMPTY = Token.of("");

    private String key;
    private String secret;

    public boolean hasKey() {
        return StringUtils.hasText(StringUtils.trimWhitespace(this.getKey()));
    }

    public boolean hasSecret() {
        return StringUtils.hasText(StringUtils.trimWhitespace(this.getSecret()));
    }

    public boolean hasContent() {
        return this.hasKey() || this.hasSecret();
    }

    public boolean isComplete() {
        return this.hasKey() && this.hasSecret();
    }

    @NotNull
    @Contract("_ -> new")
    public static Token of(final String key) {
        return new Token(key, null);
    }

    @NotNull
    @Contract("_, _ -> new")
    public static Token of(final String key, final String secret) {
        return new Token(key, secret);
    }
}
