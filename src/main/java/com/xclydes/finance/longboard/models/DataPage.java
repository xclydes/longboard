package com.xclydes.finance.longboard.models;

import java.util.List;

public class DataPage<F> {

    public final Pagination pagination;
    public final List<F> page;

    public DataPage(final Pagination pagination, final List<F> page) {
        this.pagination = pagination;
        this.page = page;
    }
}
