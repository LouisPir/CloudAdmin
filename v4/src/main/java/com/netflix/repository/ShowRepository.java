package com.netflix.repository;

import com.netflix.model.Show;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Direct replacement for V1's db.py execute_query().
 *
 * Key difference: JdbcTemplate automatically borrows a connection from
 * HikariCP, runs the query, and returns the connection — all in one call.
 * No manual get_connection() / close() needed.
 */
@Repository
public class ShowRepository {

    private final JdbcTemplate jdbc;

    // Spring injects JdbcTemplate (backed by HikariCP) automatically
    public ShowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -- Row mapper: converts a SQL row into a Show record --
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

    // ── Paginated list (same as V1's get_shows) ──────────────────
    public List<Show> findAll(int limit, int offset) {
        return jdbc.query(
            "SELECT * FROM netflix ORDER BY show_id LIMIT ? OFFSET ?",
            SHOW_MAPPER, limit, offset
        );
    }

    public int countAll() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM netflix", Integer.class);
    }

    // ── Single show by ID ────────────────────────────────────────
    public Show findById(int showId) {
        List<Show> results = jdbc.query(
            "SELECT * FROM netflix WHERE show_id = ?",
            SHOW_MAPPER, showId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    // ── Search by title ──────────────────────────────────────────
    // V1 used: WHERE title LIKE '%term%' (full table scan, ignores indexes)
    // V2 uses: MATCH(title) AGAINST('term') with the FULLTEXT index
    // Fallback to LIKE if FULLTEXT returns nothing (handles partial matches)
    public List<Show> searchByTitle(String query, int limit, int offset) {
        // Try FULLTEXT first (uses the idx_title_ft index)
        List<Show> results = jdbc.query(
            "SELECT * FROM netflix WHERE MATCH(title) AGAINST(? IN NATURAL LANGUAGE MODE) LIMIT ? OFFSET ?",
            SHOW_MAPPER, query, limit, offset
        );

        // Fallback to LIKE for partial matches (e.g. "lov" won't match FULLTEXT)
        if (results.isEmpty()) {
            results = jdbc.query(
                "SELECT * FROM netflix WHERE title LIKE ? ORDER BY title LIMIT ? OFFSET ?",
                SHOW_MAPPER, "%" + query + "%", limit, offset
            );
        }

        return results;
    }

    // ── Filter (dynamic WHERE clause, same logic as V1) ──────────
    public List<Show> filter(String category, String country, String rating,
                             String genre, Integer releaseYear,
                             int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM netflix WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null) {
            sql.append(" AND category LIKE ?");
            params.add("%" + category + "%");
        }
        if (country != null) {
            sql.append(" AND country LIKE ?");
            params.add("%" + country + "%");
        }
        if (rating != null) {
            sql.append(" AND rating LIKE ?");
            params.add("%" + rating + "%");
        }
        if (genre != null) {
            sql.append(" AND genre LIKE ?");
            params.add("%" + genre + "%");
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
