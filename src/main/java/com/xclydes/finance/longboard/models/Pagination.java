package com.xclydes.finance.longboard.models;

public class Pagination {

    public static final Pagination UNKNOWN = new Pagination(0,0,0,0);

    public final int pageSize;
    public final int currentPage;
    public final int totalPages;
    public final int totalCount;

    public Pagination(final Integer totalCount) {
        this(totalCount, 1, 1, totalCount);
    }

    public Pagination(final Integer pageSize, final Integer currentPage, final Integer totalPages, final Integer totalCount) {
        this.pageSize = orZero(pageSize);
        this.currentPage = orZero(currentPage);
        this.totalPages = orZero(totalPages);
        this.totalCount = orZero(totalCount);
    }

    private static int orZero(final Integer i) {
        return i != null ? i : 0;
    }
}
