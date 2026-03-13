-- ============================================================
-- V2 INDEXES — Run this ONCE on your existing database_netflix
-- ============================================================
-- These target the exact query patterns from routes.py / loadtest.js:
--
--   1. show_id lookup   → already covered by PRIMARY KEY
--   2. title LIKE '%x%' → can't use B-tree index (leading wildcard),
--                          but FULLTEXT index helps if we rewrite the query
--   3. category filter  → B-tree index
--   4. country filter   → B-tree index
--   5. rating filter    → B-tree index
--   6. release_year     → B-tree index
--   7. genre filter     → B-tree index
--   8. director GROUP BY → B-tree index
--   9. date_added year extraction → B-tree index
--
-- Trade-off: indexes use extra disk space and slow down INSERTs/UPDATEs.
--            Our workload is 100% reads, so this is pure upside.
-- ============================================================

USE database_netflix;

-- For filter endpoint: WHERE category LIKE ...
CREATE INDEX idx_category ON netflix(category);

-- For filter endpoint: WHERE country LIKE ...
CREATE INDEX idx_country ON netflix(country);

-- For filter endpoint: WHERE rating LIKE ...
CREATE INDEX idx_rating ON netflix(rating);

-- For filter endpoint: WHERE release_year = ...
CREATE INDEX idx_release_year ON netflix(release_year);

-- For filter + top-genres: WHERE genre LIKE ... / GROUP BY genre
CREATE INDEX idx_genre ON netflix(genre);

-- For top-directors: GROUP BY director (also filters out NULLs)
CREATE INDEX idx_director ON netflix(director);

-- For stats/yearly: GROUP BY YEAR(date_added)
CREATE INDEX idx_date_added ON netflix(date_added);

-- For title search: FULLTEXT lets us use MATCH() AGAINST() instead of LIKE '%x%'
-- LIKE '%love%' does a full table scan even with a B-tree index.
-- MATCH(title) AGAINST('love') uses the FULLTEXT index — much faster.
ALTER TABLE netflix ADD FULLTEXT INDEX idx_title_ft(title);

-- Composite index for common filter combos (category + country, category + rating)
CREATE INDEX idx_category_country ON netflix(category, country);
CREATE INDEX idx_category_rating ON netflix(category, rating);
