import mysql.connector
from mysql.connector import pooling

# Connection pool for better concurrency handling
pool = pooling.MySQLConnectionPool(
    pool_name="netflix_pool",
    pool_size=5,
    host="localhost",
    port=3306,
    user="root",
    password="root",
    database="database_netflix"
)


def get_connection():
    """Get a connection from the pool."""
    return pool.get_connection()


def execute_query(query, params=None):
    """Execute a SELECT query and return results as list of dicts."""
    conn = get_connection()
    try:
        cursor = conn.cursor(dictionary=True)
        cursor.execute(query, params or ())
        results = cursor.fetchall()
        return results
    finally:
        cursor.close()
        conn.close()
