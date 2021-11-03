package com.xclydes.finance.longboard.graphql;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.function.Supplier;

public class DateCoercing implements Coercing<LocalDate, String> {

    public static final Supplier<LocalDate> SupplyNull = () -> null;
    public static final Supplier<LocalDate> SupplyNow = LocalDate::now;

    private final DateTimeFormatter formatter;
    private final Supplier<LocalDate> defaultSupplier;

    public DateCoercing(final DateTimeFormatter formatter) {
        this(formatter, SupplyNull);
    }

    public DateCoercing(final DateTimeFormatter formatter, final Supplier<LocalDate> defaultSupplier) {
        this.formatter = formatter;
        this.defaultSupplier = defaultSupplier;
    }

    public DateTimeFormatter getFormatter() {
        return formatter;
    }

    public Supplier<LocalDate> getDefaultSupplier() {
        return defaultSupplier;
    }

    @Override
    public String serialize(@NotNull final Object dataFetcherResult) {
        if (dataFetcherResult instanceof TemporalAccessor) {
            return getFormatter().format( (TemporalAccessor) dataFetcherResult );
        } else {
            throw new CoercingSerializeException("Expected a LocalDate object.");
        }
    }

    @NotNull
    @Override
    public LocalDate parseValue(@NotNull final Object rawInput) throws CoercingParseValueException {
        try {
            if (rawInput instanceof String) {
                final String input = String.valueOf(rawInput);
                return StringUtils.hasText(input) ?
                        LocalDate.parse(input) :
                        getDefaultSupplier().get();
            } else {
                throw new CoercingParseValueException("Expected a String");
            }
        } catch (DateTimeParseException e) {
            throw new CoercingParseValueException(String.format("Not a valid date: '%s'.", rawInput), e
            );
        }
    }

    @NotNull
    @Override
    public LocalDate parseLiteral(@NotNull final Object rawInput) throws CoercingParseLiteralException {
        if (rawInput instanceof StringValue) {
            try {
                // Cast for access
                final StringValue inputValue = (StringValue) rawInput;
                // Unwrap the value
                final String inputStr = inputValue.getValue();
                // Let the other method handle it
                return this.parseValue(inputStr);
            } catch ( DateTimeParseException e ) {
                throw new CoercingParseLiteralException(e);
            }
        } else {
            throw new CoercingParseLiteralException("Expected a StringValue.");
        }
    }
}
