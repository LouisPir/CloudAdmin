from flask import Blueprint, jsonify, request
from db import execute_query

shows_bp = Blueprint("shows", __name__)


# ---------- GET all shows (with optional pagination) ----------
@shows_bp.route("/shows", methods=["GET"])
def get_shows():
    """
    List shows with pagination.
    Query params: page (default 1), per_page (default 20)
    """
    page = request.args.get("page", 1, type=int)
    per_page = request.args.get("per_page", 20, type=int)
    offset = (page - 1) * per_page

    shows = execute_query(
        "SELECT * FROM netflix ORDER BY show_id LIMIT %s OFFSET %s",
        (per_page, offset)
    )
    total = execute_query("SELECT COUNT(*) AS total FROM netflix")[0]["total"]

    return jsonify({
        "page": page,
        "per_page": per_page,
        "total": total,
        "results": shows
    })


# ---------- GET a single show by ID ----------
@shows_bp.route("/shows/<int:show_id>", methods=["GET"])
def get_show(show_id):
    results = execute_query(
        "SELECT * FROM netflix WHERE show_id = %s", (show_id,)
    )
    if not results:
        return jsonify({"error": "Show not found"}), 404
    return jsonify(results[0])


# ---------- Search shows by title ----------
@shows_bp.route("/shows/search", methods=["GET"])
def search_shows():
    """
    Search by title substring.
    Query params: q (search term), page, per_page
    """
    q = request.args.get("q", "")
    if not q:
        return jsonify({"error": "Query parameter 'q' is required"}), 400

    page = request.args.get("page", 1, type=int)
    per_page = request.args.get("per_page", 20, type=int)
    offset = (page - 1) * per_page

    shows = execute_query(
        "SELECT * FROM netflix WHERE title LIKE %s ORDER BY title LIMIT %s OFFSET %s",
        (f"%{q}%", per_page, offset)
    )
    return jsonify({"query": q, "count": len(shows), "results": shows})


# ---------- Filter shows ----------
@shows_bp.route("/shows/filter", methods=["GET"])
def filter_shows():
    """
    Filter by category, country, rating, release_year, genre.
    All filters are optional and combined with AND.
    """
    filters = []
    params = []

    for col in ["category", "country", "rating", "genre"]:
        val = request.args.get(col)
        if val:
            filters.append(f"{col} LIKE %s")
            params.append(f"%{val}%")

    year = request.args.get("release_year", type=int)
    if year:
        filters.append("release_year = %s")
        params.append(year)

    where = " AND ".join(filters) if filters else "1=1"
    page = request.args.get("page", 1, type=int)
    per_page = request.args.get("per_page", 20, type=int)
    offset = (page - 1) * per_page

    query = f"SELECT * FROM netflix WHERE {where} ORDER BY title LIMIT %s OFFSET %s"
    params.extend([per_page, offset])

    shows = execute_query(query, tuple(params))
    return jsonify({"filters_applied": {k: request.args[k] for k in request.args if k not in ("page", "per_page")},
                     "count": len(shows), "results": shows})


# ---------- Top N directors by number of titles ----------
@shows_bp.route("/shows/top-directors", methods=["GET"])
def top_directors():
    """
    Top directors by number of titles.
    Query param: n (default 10)
    """
    n = request.args.get("n", 10, type=int)
    results = execute_query(
        """SELECT director, COUNT(*) AS title_count
           FROM netflix
           WHERE director IS NOT NULL AND director != ''
           GROUP BY director
           ORDER BY title_count DESC
           LIMIT %s""",
        (n,)
    )
    return jsonify({"top": n, "results": results})


# ---------- Content count by category (Movie vs TV Show) ----------
@shows_bp.route("/shows/stats/categories", methods=["GET"])
def category_stats():
    results = execute_query(
        """SELECT category, COUNT(*) AS count
           FROM netflix
           GROUP BY category"""
    )
    return jsonify({"results": results})


# ---------- Content added per year ----------
@shows_bp.route("/shows/stats/yearly", methods=["GET"])
def yearly_stats():
    results = execute_query(
        """SELECT YEAR(date_added) AS year, COUNT(*) AS count
           FROM netflix
           WHERE date_added IS NOT NULL
           GROUP BY YEAR(date_added)
           ORDER BY year"""
    )
    return jsonify({"results": results})


# ---------- Top N genres ----------
@shows_bp.route("/shows/top-genres", methods=["GET"])
def top_genres():
    n = request.args.get("n", 10, type=int)
    results = execute_query(
        """SELECT genre, COUNT(*) AS count
           FROM netflix
           GROUP BY genre
           ORDER BY count DESC
           LIMIT %s""",
        (n,)
    )
    return jsonify({"top": n, "results": results})
