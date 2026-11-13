"""Reduces the GeoNames `cities15000` dump to the bundled list used by the city search.

Usage, from the repository root, with the files downloaded from https://download.geonames.org/export/dump/
(`cities15000.zip` unzipped and `admin1CodesASCII.txt`, CC BY 4.0) placed outside the repository:

    python3 -I tools/cities/reduce.py <cities15000.txt> <admin1CodesASCII.txt> app/src/main/assets/cities.tsv

One line per place, in the `Cities.encode` format (weather/Cities.kt): GeoNames ID, name, ASCII name (empty if
identical), country (ISO 3166-1), subdivision in English, latitude and longitude to 2 decimals, population. Places by
decreasing population. UTF-8 text: the APK compresses its assets, and the app reads it as is.
"""
import sys


def clean(value):
    return value.replace("\t", " ").replace("\n", " ").replace("\r", " ").strip()


def main(cities_path, admin1_path, out_path):
    regions = {}
    with open(admin1_path, encoding="utf-8") as f:
        for line in f:
            fields = line.rstrip("\n").split("\t")
            if len(fields) >= 2:
                regions[fields[0]] = clean(fields[1])
    rows = []
    with open(cities_path, encoding="utf-8") as f:
        for line in f:
            r = line.rstrip("\n").split("\t")
            if len(r) < 19:
                continue
            name, ascii_name, cc = clean(r[1]), clean(r[2]), r[8]
            rows.append((
                int(r[14] or 0),
                "\t".join([
                    r[0], name, "" if ascii_name == name else ascii_name, cc, regions.get(cc + "." + r[10], ""),
                    "%.2f" % float(r[4]), "%.2f" % float(r[5]), str(int(r[14] or 0)),
                ]),
            ))
    rows.sort(key=lambda row: -row[0])
    data = ("\n".join(row[1] for row in rows) + "\n").encode("utf-8")
    with open(out_path, "wb") as out:
        out.write(data)
    print(len(rows), "places,", len(data), "bytes")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
