package com.seatsync.common.contracts;

import com.seatsync.common.model.ShowDetails;
import com.seatsync.common.model.ShowSummary;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for multi-show catalog discovery and auditorium layout retrieval.
 */
public interface CatalogService {

    /**
     * @return List of all scheduled shows
     */
    List<ShowSummary> getShows();

    /**
     * Retrieves detailed show metadata and auditorium layout.
     *
     * @param showId Show identifier
     * @return Optional containing ShowDetails if found
     */
    Optional<ShowDetails> getShowDetails(String showId);
}
