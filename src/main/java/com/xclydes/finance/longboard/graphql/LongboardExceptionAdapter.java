package com.xclydes.finance.longboard.graphql;

import graphql.ErrorClassification;
import org.springframework.graphql.execution.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LongboardExceptionAdapter implements GraphQLError {

    protected static final SourceLocation NO_WHERE = new SourceLocation(-1, -1);

    private final ErrorClassification classification;
    private final String message;
    private final List<SourceLocation> locations;
    private final Map<String, Object> extensions;

    public LongboardExceptionAdapter(final String message) {
        this(ErrorType.INTERNAL_ERROR, message);
    }

    public LongboardExceptionAdapter(final ErrorClassification classification, final String message) {
        this(classification, message, Collections.emptyMap());
    }

    public LongboardExceptionAdapter(final ErrorClassification classification, final String message, final Map<String, Object> extensions) {
        this(classification, message, Collections.singletonList(NO_WHERE), extensions);
    }

    public LongboardExceptionAdapter(final ErrorClassification classification, final String message, final List<SourceLocation> locations, final Map<String, Object> extensions) {
        this.classification = classification;
        this.message = message;
        this.locations = locations;
        this.extensions = extensions;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return this.locations;
    }

    @Override
    public ErrorClassification getErrorType() {
        return this.classification;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return this.extensions;
    }
}
