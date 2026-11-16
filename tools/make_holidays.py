#!/usr/bin/env python3
"""
Writes app/src/main/assets/holidays.json: national public holiday dates for a
few countries, so "Public holidays" in Settings works without the internet.

GoalGate uses these dates in two places:
  - Workday goals are hidden on a public holiday (it is a day off).
  - If "Public holidays" is on under Downtime, the whole day counts as downtime.

The dates come from the open-source "holidays" package (MIT licence):
    pip install holidays
    python3 tools/make_holidays.py
Only the dates are bundled, not the package. Re-run it to add later years;
the app says which years it covers. National holidays only: regional ones
(a single state or province) are left out.
"""
import json
import os
import sys

try:
    import holidays
except ImportError:
    sys.exit("Install the generator's one dependency first:  pip install holidays")

COUNTRIES = ["KR", "US", "GB", "CA", "AU", "JP"]
FIRST_YEAR, LAST_YEAR = 2026, 2030


def main():
    out = {"firstYear": FIRST_YEAR, "lastYear": LAST_YEAR, "source": f"holidays {holidays.__version__}", "countries": {}}
    for code in COUNTRIES:
        dates = sorted(
            d.isoformat()
            for d in holidays.country_holidays(code, years=range(FIRST_YEAR, LAST_YEAR + 1)).keys()
        )
        # Checks: every year has holidays, New Year's Day is there, no duplicates.
        assert len(dates) == len(set(dates)), code
        for year in range(FIRST_YEAR, LAST_YEAR + 1):
            in_year = [d for d in dates if d.startswith(str(year))]
            assert len(in_year) >= 5, f"{code} {year}: only {len(in_year)} holidays"
            assert f"{year}-01-01" in in_year, f"{code} {year}: no New Year's Day"
        out["countries"][code] = dates

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    path = os.path.join(root, "app", "src", "main", "assets", "holidays.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=1)
        f.write("\n")
    counts = ", ".join(f"{c} {len(v)}" for c, v in out["countries"].items())
    print(f"Wrote {os.path.relpath(path, root)} ({FIRST_YEAR} to {LAST_YEAR}): {counts}")


if __name__ == "__main__":
    main()
