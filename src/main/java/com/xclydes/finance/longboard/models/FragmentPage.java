package com.xclydes.finance.longboard.models;

import java.util.List;

public class FragmentPage<F> {

    public final Pagination pagination;
    public final List<F> fragments;

    public FragmentPage(final Pagination pagination, final List<F> fragments) {
        this.pagination = pagination;
        this.fragments = fragments;
    }
}
