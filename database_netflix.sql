DROP DATABASE IF EXISTS database_netflix;
CREATE DATABASE IF NOT EXISTS database_netflix;
USE database_netflix;
CREATE TABLE netflix (
    show_id INT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50),
    title VARCHAR(200),
    director VARCHAR(250),
    cast TEXT,
    country VARCHAR(200),
    date_added DATE,
    release_year YEAR,
    rating VARCHAR(20),
    duration VARCHAR(50),         
    genre VARCHAR(100),
    description TEXT 
);
LOAD DATA INFILE 'C:/ProgramData/MySQL/MySQL Server 8.0/Uploads/netflix_titles.csv'
INTO TABLE netflix
FIELDS TERMINATED BY ',' 
ENCLOSED BY '"'
LINES TERMINATED BY '\r\n'
IGNORE 1 LINES
(show_id, category, title, director, cast, country, @date_added, release_year, rating, duration, genre, description);


#select * from netflix;

