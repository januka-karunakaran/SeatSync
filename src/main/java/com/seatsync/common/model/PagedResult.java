package com.seatsync.common.model;

import java.util.List;

/**
 * Generic paged result container enforcing bounded page sizes.
 */
public record PagedResult<T>(
    List<T> items,
    int pageNumber,
    int pageSize,
    long totalItems,
    boolean hasMore
) {
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    public PagedResult {
        items = (items == null) ? List.of() : List.copyOf(items);
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number must be non-negative: " + pageNumber);
        }
    }
}
