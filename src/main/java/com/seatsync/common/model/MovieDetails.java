package com.seatsync.common.model;

/** Public movie metadata; scheduling remains show-scoped. */
public record MovieDetails(String movieId, String title, String description, String genre, int durationMinutes) {
    public MovieDetails {
        if (movieId == null || !movieId.matches("[A-Za-z0-9._-]{1,64}")
                || title == null || title.isBlank() || title.length() > 120
                || description == null || description.length() > 512
                || genre == null || genre.isBlank() || genre.length() > 80
                || durationMinutes < 1 || durationMinutes > 600
                || (title + description + genre).chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid movie metadata");
        }
    }
}
