package com.seatsync.common.contracts;

import com.seatsync.common.model.BookingHistoryItem;
import com.seatsync.common.model.PagedResult;

/**
 * Service contract for paginated user booking history queries.
 */
public interface HistoryService {

    /**
     * Queries booking history with bounded pagination.
     *
     * @param userId     Authenticated user ID
     * @param pageNumber Zero-based page index
     * @param pageSize   Items per page (capped at {@link PagedResult#MAX_PAGE_SIZE})
     * @return PagedResult of booking items
     */
    PagedResult<BookingHistoryItem> getBookingHistory(String userId, int pageNumber, int pageSize);
}
