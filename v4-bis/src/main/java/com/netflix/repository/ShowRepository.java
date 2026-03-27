package com.netflix.repository;

import com.netflix.model.Show;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// V4-bis-fix – column projection on list + paginated list cache
@Repository
public class ShowRepository {

    private final JdbcTemplate jdbc;

    public ShowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -- Full row mapper (for single show, search, filter) --
    private static final RowMapper<Show> SHOW_MAPPER = (rs, rowNum) -> new Show(
        rs.getInt("show_id"),
        rs.getString("category"),
        rs.getString("title"),
        rs.getString("director"),
        rs.getString("cast"),
        rs.getString("country"),
        rs.getDate("date_added") != null
            ? rs.getDate("date_added").toLocalDate()
            : null,
        rs.getInt("release_year"),
        rs.getString("rating"),
        rs.getString("duration"),
        rs.getString("genre"),
        rs.getString("description")
    );

    // -- Lightweight mapper: skips description & cast (list view) --
    private static final String LIST_COLUMNS =
        "show_id, category, title, director, country, date_added, " +
        "release_year, rating, duration, genre";

    private static final RowMapper<Show> LIST_MAPPER = (rs, rowNum) -> new Show(
        rs.getInt("show_id"),
        rs.getString("category"),
        rs.getString("title"),
        rs.getString("director"),
        null,                       // cast  — not needed in list view
        rs.getString("country"),
        rs.getDate("date_added") != null
            ? rs.getDate("date_added").toLocalDate()
            : null,
        rs.getInt("release_year"),
        rs.getString("rating"),
        rs.getString("duration"),
        rs.getString("genre"),
        null                        // description — not needed in list view
    );

    // ── Lightweight in-memory cache for paginated lists ──────────
    //    Key = "limit:offset", e.g. "20:0", "20:20"
    //    With 5 pages × 20 per_page in the load test, this holds ≤5 entries.
    //    Same TTL pattern as your CacheService for aggregations.
    private final ConcurrentHashMap<String, CachedList> listCache = new ConcurrentHashMap<>();
    private static final long LIST_CACHE_TTL_MS = 10_000; // 10 seconds

    private record CachedList(List<Show> shows, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    // ── Paginated list (column projection + cache) ───────────────
    public List<Show> findAll(int limit, int offset) {
        String key = limit + ":" + offset;

        CachedList cached = listCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.shows();
        }

        List<Show> results = jdbc.query(
            "SELECT " + LIST_COLUMNS + " FROM netflix ORDER BY show_id LIMIT ? OFFSET ?",
            LIST_MAPPER, limit, offset
        );

        listCache.put(key, new CachedList(results, System.currentTimeMillis() + LIST_CACHE_TTL_MS));
        return results;
    }

    // ── Count (unchanged — already trivial) ──────────────────────
    private volatile int cachedCount = -1;
    private volatile long countExpiresAt = 0;

    public int countAll() {
        long now = System.currentTimeMillis();
        if (cachedCount > 0 && now < countExpiresAt) {
            return cachedCount;
        }
        cachedCount = jdbc.queryForObject("SELECT COUNT(*) FROM netflix", Integer.class);
        countExpiresAt = now + LIST_CACHE_TTL_MS;
        return cachedCount;
    }

    // ── Single show by ID (full row — unchanged) ─────────────────
    public Show findById(int showId) {
        List<Show> results = jdbc.query(
            "SELECT * FROM netflix WHERE show_id = ?",
            SHOW_MAPPER, showId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    // ── Search by title (FULLTEXT → LIKE fallback) ───────────────
    public List<Show> searchByTitle(String query, int limit, int offset) {
        List<Show> results = jdbc.query(
            "SELECT * FROM netflix WHERE MATCH(title) AGAINST(? IN NATURAL LANGUAGE MODE) LIMIT ? OFFSET ?",
            SHOW_MAPPER, query, limit, offset
        );

        if (results.isEmpty()) {
            results = jdbc.query(
                "SELECT * FROM netflix WHERE title LIKE ? ORDER BY title LIMIT ? OFFSET ?",
                SHOW_MAPPER, "%" + query + "%", limit, offset
            );
        }

        return results;
    }

    // ── Filter (exact-match WHERE clause → B-Tree index hits) ────
    public List<Show> filter(String category, String country, String rating,
                             String genre, Integer releaseYear,
                             int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM netflix WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (country != null) {
            sql.append(" AND country = ?");
            params.add(country);
        }
        if (rating != null) {
            sql.append(" AND rating = ?");
            params.add(rating);
        }
        if (genre != null) {
            sql.append(" AND genre = ?");
            params.add(genre);
        }
        if (releaseYear != null) {
            sql.append(" AND release_year = ?");
            params.add(releaseYear);
        }

        sql.append(" ORDER BY title LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), SHOW_MAPPER, params.toArray());
    }

    // ── Top N directors ──────────────────────────────────────────
    public List<Map<String, Object>> topDirectors(int n) {
        return jdbc.queryForList(
            """
            SELECT director, COUNT(*) AS title_count
            FROM netflix
            WHERE director IS NOT NULL AND director != ''
            GROUP BY director
            ORDER BY title_count DESC
            LIMIT ?
            """, n
        );
    }

    // ── Top N genres ─────────────────────────────────────────────
    public List<Map<String, Object>> topGenres(int n) {
        return jdbc.queryForList(
            """
            SELECT genre, COUNT(*) AS count
            FROM netflix
            GROUP BY genre
            ORDER BY count DESC
            LIMIT ?
            """, n
        );
    }

    // ── Stats: category breakdown ────────────────────────────────
    public List<Map<String, Object>> categoryStats() {
        return jdbc.queryForList(
            "SELECT category, COUNT(*) AS count FROM netflix GROUP BY category"
        );
    }

    // ── Stats: content added per year ────────────────────────────
    public List<Map<String, Object>> yearlyStats() {
        return jdbc.queryForList(
            """
            SELECT YEAR(date_added) AS year, COUNT(*) AS count
            FROM netflix
            WHERE date_added IS NOT NULL
            GROUP BY YEAR(date_added)
            ORDER BY year
            """
        );
    }
}
