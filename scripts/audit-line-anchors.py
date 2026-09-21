"""Report effective geographic anchors; run from any directory (Python stdlib only).

Reads the Java overrides/bounds rather than keeping a second, drifting copy.
This diagnoses coverage, not whether a coordinate has been externally verified.
"""
import argparse
import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/futek/railroad/layout/GeoAnchorCalculator.java"


def java_map(source, name):
    body = re.search(r"\b" + name + r"\s*=\s*(.*?);", source, re.S).group(1)
    return {key: tuple(float(x.strip()) for x in values.split(","))
            for key, values in re.findall(r'"([^"\n]+)"\s*,\s*new double\[\]\s*\{([^}]+)\}', body)}


def load_coordinates():
    source = JAVA.read_text(encoding="utf-8")
    overrides = java_map(source, "COORDINATE_OVERRIDES")
    bounds = java_map(source, "REGION_BOUNDS")
    with (ROOT / "data/raw/station-coordinates.csv").open(encoding="utf-8-sig", newline="") as f:
        coords = {r["name"]: (float(r["lat"]), float(r["lng"])) for r in csv.DictReader(f)}
    coords.update({name: point for name, point in overrides.items() if name in coords})
    with (ROOT / "data/raw/station-coordinates-verified.csv").open(encoding="utf-8", newline="") as f:
        coords.update({r["name"]: (float(r["lat"]), float(r["lng"])) for r in csv.DictReader(f)})
    return coords, bounds


def plausible(point, regions, bounds):
    return not regions or any(r in bounds and bounds[r][0] <= point[0] <= bounds[r][1]
                             and bounds[r][2] <= point[1] <= bounds[r][3] for r in regions)


def audit():
    coords, bounds = load_coordinates()
    with (ROOT / "data/raw/station-coordinates-verified.csv").open(encoding="utf-8", newline="") as f:
        checked = {r["name"] for r in csv.DictReader(f)}
    results = []
    for path in sorted((ROOT / "data/raw/lines").glob("*.json")):
        line = json.loads(path.read_text(encoding="utf-8-sig"))
        names = [s["name"] for s in line["stations"]]
        regions = line.get("region") or []
        anchors = [i for i, name in enumerate(names)
                   if name in coords and (name in checked or plausible(coords[name], regions, bounds))]
        rejected = [name for name in names if name in coords and name not in checked
                    and not plausible(coords[name], regions, bounds)]
        # Extrapolation uses the nearest endpoint pair, just like interpolate().
        overshoot = None
        if len(anchors) >= 2:
            overshoot = max(anchors[0] / (anchors[1] - anchors[0]),
                            (len(names) - 1 - anchors[-1]) / (anchors[-1] - anchors[-2]))
        results.append(dict(file=path.name, line=line["lineName"], segment=line.get("segmentLabel"),
                            stations=len(names), anchors=len(anchors),
                            source_checked=sum(name in checked for name in names),
                            anchor_names=[names[i] for i in anchors], rejected=rejected,
                            missing=[name for name in names if name not in coords],
                            max_overshoot=round(overshoot, 3) if overshoot is not None else None))
    return results


def markdown(results):
    rows = ["| 노선 파일 | 역 수 | 유효 닻 | 외부 출처 대조 | 최대 외삽 비율 | 지역 검사 제외 |",
            "|---|---:|---:|---:|---:|---|"]
    for r in results:
        ratio = "—" if r["max_overshoot"] is None else str(r["max_overshoot"])
        rows.append(f'| {r["file"]} | {r["stations"]} | {r["anchors"]} | {r["source_checked"]} '
                    f'| {ratio} | {", ".join(r["rejected"]) or "—"} |')
    return "\n".join(rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Print full machine-readable results")
    parser.add_argument("--markdown", action="store_true", help="Print a per-line review table")
    args = parser.parse_args()
    results = audit()
    if args.json:
        print(json.dumps(results, ensure_ascii=False, indent=2))
    elif args.markdown:
        print(markdown(results))
    else:
        for r in results:
            ratio = "n/a" if r["max_overshoot"] is None else str(r["max_overshoot"])
            print(f'{r["file"]}: {r["anchors"]}/{r["stations"]} anchors; overshoot={ratio}; '
                  f'rejected={",".join(r["rejected"]) or "-"}')
