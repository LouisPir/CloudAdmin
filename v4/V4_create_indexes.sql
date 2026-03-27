-- ============================================================
-- V4 Index Script for netflix table
-- Run once against the database_netflix schema.
-- ============================================================

-- B-Tree indexes for the exact-match filter columns
CREATE INDEX idx_category     ON netflix (category);
CREATE INDEX idx_country      ON netflix (country);
CREATE INDEX idx_rating       ON netflix (rating);
CREATE INDEX idx_genre        ON netflix (genre);
CREATE INDEX idx_release_year ON netflix (release_year);

-- FULLTEXT index for the title search endpoint
CREATE FULLTEXT INDEX idx_title_ft ON netflix (title);
