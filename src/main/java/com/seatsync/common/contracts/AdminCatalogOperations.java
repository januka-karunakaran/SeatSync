package com.seatsync.common.contracts;

import com.seatsync.common.model.*;
import java.util.List;

/** Trusted backend boundary. Call only behind M4 authorization; mutations use the booking inventory lock. */
public interface AdminCatalogOperations {
    boolean createMovie(MovieDetails movie);
    List<MovieDetails> getMovies();
    boolean createShow(String movieId, ShowDetails show);
    boolean disableShow(String showId);
    PagedResult<AdminBookingView> listBookings(String showId, int pageNumber, int pageSize);
    boolean overrideSeat(String actorId, String showId, String seatId, SeatStatus status);
    boolean forceCancelHold(String holdId);
}
