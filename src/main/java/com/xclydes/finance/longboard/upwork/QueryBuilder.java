package com.xclydes.finance.longboard.upwork;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

public class QueryBuilder {

    public static final DateTimeFormatter dateFormatSQL = DateTimeFormatter.ISO_DATE;

    private final List<String> fields;
    private final List<String> wheres;

    private QueryBuilder() {
        this.fields = new ArrayList<>();
        this.wheres = new ArrayList<>();
    }

    public QueryBuilder field(final String ... fields) {
        return this.field(Arrays.asList(fields));
    }

    public QueryBuilder field(final Iterable<String> fields) {
        // Add each field
        StreamSupport.stream(fields.spliterator(), true)
            .filter(StringUtils::hasText)
            .map(String::toLowerCase)
            .filter(field -> !this.fields.contains(field))
            .forEach(this.fields::add);
        return this;
    }

    public QueryBuilder andWhere(final String field, final String comparator, final LocalDate date) {
        return this.andWhere(field, comparator, dateFormatSQL.format(date));
    }

    public QueryBuilder andWhere(final String field, final String comparator, final String value) {
        return this.andWhereCondition(String.format("%s %s '%s'", field, comparator, value));
    }

    public QueryBuilder andWhereCondition(final String condition) {
        String built = condition;
        // If there are existing fields
        if(!this.wheres.isEmpty()) {
            // Append the AND
            built = " AND " + condition;
        }
        this.wheres.add(built);
        return this;
    }

    public QueryBuilder orWhereCondition(final String condition) {
        String built = condition;
        // If there are existing fields
        if(!this.wheres.isEmpty()) {
            // Append the AND
            built = " OR " + condition;
        }
        this.wheres.add(built);
        return this;
    }

    public String build() {
        if(this.fields.isEmpty()) {
            throw new IllegalStateException("No fields specified");
        }
        if(this.wheres.isEmpty()) {
            throw new IllegalStateException("No where conditions specified");
        }
        return "SELECT " +
                String.join(",", this.fields) +
                " WHERE " +
                String.join(" ", this.wheres);
    }

    public static QueryBuilder get(final Iterable<String> fields) {
        final QueryBuilder bld = new QueryBuilder();
        fields.forEach(bld::field);
        return bld;
    }

    public static QueryBuilder get(final String ... fields) {
        return new QueryBuilder().field(fields);
    }
}
