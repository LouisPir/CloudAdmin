package com.netflix.controller;

import com.netflix.model.Show;
import com.netflix.repository.ShowRepository;
import com.netflix.service.CacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V3 controller — same 8 endpoints as V2, but the 4 aggregation endpoints
 * now check Redis before hitting MySQL.
 *
 * Which endpoints are cached and why:
 *   CACHED (results are the same for every request):
 *     - top-directors: GROUP BY director across full table
 *     - top-genres:    GROUP BY genre across full table
 *     - stats/categories: GROUP BY category across full table
 *     - stats/yearly:  GROUP BY YEAR(date_added) across full table
 *
 *   NOT CACHED (results vary per request):
 *     - list shows:   different page/per_page each time
 *     - single show:  different show_id each time
 *     - search:       different search term each time
 *     - filter:       different filter combinations each time
 */
@RestController
@RequestMapping("/netflix")
public class ShowController {

    private final ShowRepository repo;
    private final CacheService cache;

    public ShowController(ShowRepository repo, CacheService cache) {
        this.repo = repo;
        this.cache = cache;
    }

    // ══════════════════════════════════════════════════════════════
    // UNCACHED ENDPOINTS (same as V2 — no changes)
    // ══════════════════════════════════════════════════════════════

    @GetMapping
    public Map<String, Object> index() {
        return Map.of(
            "service", "Netflix Catalog API",
            "version", "3.0.0",
            "endpoints", Map.of(
                "GET /netflix/shows",              "List shows (paginated)",
                "GET /netflix/shows/<id>",         "Get show by ID",
                "GET /netflix/shows/search?q=",    "Search by title",
                "GET /netflix/shows/filter",       "Filter by category/country/rating/year/genre",
                "GET /netflix/shows/top-directors", "Top N directors (cached)",
                "GET /netflix/shows/top-genres",    "Top N genres (cached)",
                "GET /netflix/shows/stats/categories", "Movie vs TV Show counts (cached)",
                "GET /netflix/shows/stats/yearly",     "Content added per year (cached)"
            )
        );
    }

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

    @GetMapping("/shows/{showId}")
    public ResponseEntity<?> getShow(@PathVariable int showId) {
        Show show = repo.findById(showId);
        if (show == null) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Show not found"));
        }
        return ResponseEntity.ok(show);
    }

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

    // ══════════════════════════════════════════════════════════════
    // CACHED ENDPOINTS (V3 — go through Redis first)
    //
    // Pattern for each:
    //   1. Build a cache key that includes any parameters
    //   2. Call cache.getOrCompute(key, () -> repo.queryMethod())
    //   3. CacheService checks Redis → if hit, returns instantly
    //      If miss, calls the lambda (which hits MySQL), stores
    //      result in Redis with 60s TTL, then returns it
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    @GetMapping("/shows/top-directors")
    public Map<String, Object> topDirectors(
            @RequestParam(defaultValue = "10") int n) {

        // Key includes n so ?n=10 and ?n=5 don't collide
        String key = "netflix:top-directors:" + n;

        List<Map<String, Object>> results = (List<Map<String, Object>>)
            cache.getOrCompute(key, () -> repo.topDirectors(n));

        return Map.of("top", n, "results", results);
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/shows/top-genres")
    public Map<String, Object> topGenres(
            @RequestParam(defaultValue = "10") int n) {

        String key = "netflix:top-genres:" + n;

        List<Map<String, Object>> results = (List<Map<String, Object>>)
            cache.getOrCompute(key, () -> repo.topGenres(n));

        return Map.of("top", n, "results", results);
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/shows/stats/categories")
    public Map<String, Object> categoryStats() {

        String key = "netflix:stats:categories";

        List<Map<String, Object>> results = (List<Map<String, Object>>)
            cache.getOrCompute(key, () -> repo.categoryStats());

        return Map.of("results", results);
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/shows/stats/yearly")
    public Map<String, Object> yearlyStats() {

        String key = "netflix:stats:yearly";

        List<Map<String, Object>> results = (List<Map<String, Object>>)
            cache.getOrCompute(key, () -> repo.yearlyStats());

        return Map.of("results", results);
    }

    // ══════════════════════════════════════════════════════════════
    // CACHE MONITORING (for demo — check hit rate after load test)
    //
    // Usage:
    //   Before test:  curl -X POST http://localhost:8000/netflix/cache/reset
    //   After test:   curl http://localhost:8000/netflix/cache/stats
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        return Map.of(
            "hits", cache.getHits(),
            "misses", cache.getMisses(),
            "hit_rate", String.format("%.1f%%", cache.getHitRate() * 100)
        );
    }

    @PostMapping("/cache/reset")
    public Map<String, String> resetCache() {
        cache.resetStats();
        return Map.of("status", "Cache stats reset");
    }
}
