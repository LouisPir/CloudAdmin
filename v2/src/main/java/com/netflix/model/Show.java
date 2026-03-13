package com.netflix.model;

import java.time.LocalDate;

/**
 * Maps directly to the `netflix` table.
 * Using a record keeps it concise — no boilerplate getters/setters.
 * Jackson (Spring's JSON serializer) handles records natively.
 */
public record Show(
    int showId,
    String category,
    String title,
    String director,
    String cast,
    String country,
    LocalDate dateAdded,
    int releaseYear,
    String rating,
    String duration,
    String genre,
    String description
) {}
