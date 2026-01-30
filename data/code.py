import csv
from datetime import datetime

input_file = r"C:\Users\louis\OneDrive\Documents\4ème Année\CloudAdmin\data\netflix_titles.csv"
output_file = r"C:\Users\louis\OneDrive\Documents\4ème Année\CloudAdmin\data\netflix_titles_clean.csv"

date_formats = [
    "%m/%d/%Y",    # 9/25/2021
    "%d-%b-%y",    # 25-Sep-21
    "%d-%b-%Y",    # 25-Sep-2021
    "%B %d, %Y"    # August 4, 2017
]

def parse_date(date_str):
    date_str = date_str.strip()
    if not date_str:
        return ""

    for fmt in date_formats:
        try:
            return datetime.strptime(date_str, fmt).strftime("%Y-%m-%d")
        except ValueError:
            continue

    print(f"⚠️ Date non reconnue : {date_str}")
    return ""

with open(input_file, newline="", encoding="utf-8") as infile, \
     open(output_file, "w", newline="", encoding="utf-8") as outfile:

    reader = csv.DictReader(infile)
    writer = csv.DictWriter(outfile, fieldnames=reader.fieldnames)

    writer.writeheader()

    for row in reader:
        row["show_id"] = row["show_id"].lstrip("s")
        row["date_added"] = parse_date(row["date_added"])
        writer.writerow(row)

print("✅ CSV cleaned successfully (YYYY-MM-DD)")
