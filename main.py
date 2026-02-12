from flask import Flask
from routes import shows_bp

app = Flask(__name__)

# Register the blueprint with /netflix prefix
app.register_blueprint(shows_bp, url_prefix="/netflix")


@app.route("/")
def index():
    return {
        "service": "Netflix Catalog API",
        "version": "1.0.0",
        "endpoints": {
            "GET /netflix/shows":              "List shows (paginated)",
            "GET /netflix/shows/<id>":         "Get show by ID",
            "GET /netflix/shows/search?q=":    "Search by title",
            "GET /netflix/shows/filter":       "Filter by category/country/rating/year/genre",
            "GET /netflix/shows/top-directors": "Top N directors",
            "GET /netflix/shows/top-genres":    "Top N genres",
            "GET /netflix/shows/stats/categories": "Movie vs TV Show counts",
            "GET /netflix/shows/stats/yearly":     "Content added per year",
        }
    }


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=8000, debug=True)
