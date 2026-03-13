package com.netflix.controller;

import com.netflix.model.Show;
import com.netflix.repository.ShowRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct replacement for V1's routes.py.
 *
 * URL mapping:
 *   V1: app.register_blueprint(shows_bp, url_prefix="/netflix")
 *   V2: @RequestMapping("/netflix") on this controller
 *
 * Every method here runs on a separate Tomcat thread.
 * With 200 threads + 20 DB connections, this handles 100 VUs easily.
 */
@RestController
@RequestMapping("/netflix")
public class ShowController {

    private final ShowRepository repo;

    public ShowController(ShowRepository repo) {
        this.repo = repo;
    }

    // ── GET / (index) ────────────────────────────────────────────
    @GetMapping
    public Map<String, Object> index() {
        return Map.of(
            "service", "Netflix Catalog API",
            "version", "2.0.0",
            "endpoints", Map.of(
                "GET /netflix/shows",              "List shows (paginated)",
                "GET /netflix/shows/<id>",         "Get show by ID",
                "GET /netflix/shows/search?q=",    "Search by title",
                "GET /netflix/shows/filter",       "Filter by category/country/rating/year/genre",
                "GET /netflix/shows/top-directors", "Top N directors",
                "GET /netflix/shows/top-genres",    "Top N genres",
                "GET /netflix/shows/stats/categories", "Movie vs TV Show counts",
                "GET /netflix/shows/stats/yearly",     "Content added per year"
            )
        );
    }

    // ── GET /shows — paginated list ──────────────────────────────
    @GetMapping("/shows")
    public Map<String, Object> getShows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {

        int offset = (page - 1) * perPage;
        List<Show> shows = repo.findAll(perPage, offset);
        int total = repo.countAll();

        return Map.of(
            "page", page,
            "per_page", perPage,
            "total", total,
            "results", shows
        );
    }

    // ── GET /shows/{id} — single show ────────────────────────────
    @GetMapping("/shows/{showId}")
    public ResponseEntity<?> getShow(@PathVariable int showId) {
        Show show = repo.findById(showId);
        if (show == null) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Show not found"));
        }
        return ResponseEntity.ok(show);
    }

    // ── GET /shows/search?q= — title search ─────────────────────
    @GetMapping("/shows/search")
    public ResponseEntity<?> searchShows(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Query parameter 'q' is required"));
        }

        int offset = (page - 1) * perPage;
        List<Show> shows = repo.searchByTitle(q, perPage, offset);

        return ResponseEntity.ok(Map.of(
            "query", q,
            "count", shows.size(),
            "results", shows
        ));
    }

    // ── GET /shows/filter — dynamic filters ──────────────────────
    @GetMapping("/shows/filter")
    public Map<String, Object> filterShows(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String genre,
            @RequestParam(name = "release_year", required = false) Integer releaseYear,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {

        int offset = (page - 1) * perPage;
        List<Show> shows = repo.filter(category, country, rating, genre, releaseYear, perPage, offset);

        // Build filters_applied map (same as V1)
        Map<String, String> appliedFilters = new HashMap<>();
        if (category != null) appliedFilters.put("category", category);
        if (country != null) appliedFilters.put("country", country);
        if (rating != null) appliedFilters.put("rating", rating);
        if (genre != null) appliedFilters.put("genre", genre);
        if (releaseYear != null) appliedFilters.put("release_year", String.valueOf(releaseYear));

        return Map.of(
            "filters_applied", appliedFilters,
            "count", shows.size(),
            "results", shows
        );
    }

    // ── GET /shows/top-directors?n=10 ────────────────────────────
    @GetMapping("/shows/top-directors")
    public Map<String, Object> topDirectors(
            @RequestParam(defaultValue = "10") int n) {
        return Map.of("top", n, "results", repo.topDirectors(n));
    }

    // ── GET /shows/top-genres?n=10 ───────────────────────────────
    @GetMapping("/shows/top-genres")
    public Map<String, Object> topGenres(
            @RequestParam(defaultValue = "10") int n) {
        return Map.of("top", n, "results", repo.topGenres(n));
    }

    // ── GET /shows/stats/categories ──────────────────────────────
    @GetMapping("/shows/stats/categories")
    public Map<String, Object> categoryStats() {
        return Map.of("results", repo.categoryStats());
    }

    // ── GET /shows/stats/yearly ──────────────────────────────────
    @GetMapping("/shows/stats/yearly")
    public Map<String, Object> yearlyStats() {
        return Map.of("results", repo.yearlyStats());
    }
}
