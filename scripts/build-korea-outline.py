"""src/main/resources/static/data/korea-outline.json을 다시 만드는 스크립트.

southkorea/southkorea-maps 저장소의 비단순화(전체 해상도) 시도 경계 GeoJSON을 내려받아,
GeoAnchorCalculator.projectToCanvas()와 완전히 동일한 고정 범위·공식으로 각 좌표를 투영한
뒤, Douglas-Peucker로 단순화해서 브라우저에서 그리기 적당한 크기로 줄인다.

원본(skorea_provinces_geo.json, 약 28MB)은 저장소에 커밋하지 않는다 — 이 스크립트를 다시
돌리면 매번 새로 받아오면 된다. GeoAnchorCalculator의 LAT_MIN/LAT_MAX/LNG_MIN/LNG_MAX
상수를 바꾸면 이 스크립트도 다시 돌려야 정렬이 맞는다.

사용법:
    python scripts/build-korea-outline.py

필요하면 --tolerance(투영 좌표계 기준 픽셀, 클수록 더 단순화됨)와 --min-area(이보다
작은 섬은 통째로 버림)를 조정해서 상세도/파일 크기를 조절할 수 있다.
"""

import argparse
import json
import math
import urllib.request
from pathlib import Path

SOURCE_URL = (
    "https://raw.githubusercontent.com/southkorea/southkorea-maps/master/"
    "kostat/2013/json/skorea_provinces_geo.json"
)

# GeoAnchorCalculator의 상수와 반드시 일치해야 한다.
LAT_MIN, LAT_MAX = 33.0, 38.65
LNG_MIN, LNG_MAX = 124.5, 129.6
AREA_WIDTH = AREA_HEIGHT = 4000.0
CANVAS_MARGIN_RATIO = 0.08

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_PATH = REPO_ROOT / "src/main/resources/static/data/korea-outline.json"


def make_projector():
    mean_lat = (LAT_MIN + LAT_MAX) / 2
    lng_scale = math.cos(math.radians(mean_lat))
    width_deg = (LNG_MAX - LNG_MIN) * lng_scale
    height_deg = LAT_MAX - LAT_MIN
    margin = AREA_WIDTH * CANVAS_MARGIN_RATIO
    target_w = AREA_WIDTH - margin * 2
    target_h = AREA_HEIGHT - margin * 2
    scale = min(target_w / width_deg, target_h / height_deg)
    offset_x = margin + (target_w - width_deg * scale) / 2
    offset_y = margin + (target_h - height_deg * scale) / 2

    def project(lng, lat):
        x = offset_x + (lng - LNG_MIN) * lng_scale * scale
        y = offset_y + (LAT_MAX - lat) * scale
        return [round(x, 1), round(y, 1)]

    return project


def dist_point_to_segment(p, a, b):
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def douglas_peucker(points, tolerance):
    if len(points) < 3:
        return points
    dmax, index = 0.0, 0
    for i in range(1, len(points) - 1):
        d = dist_point_to_segment(points[i], points[0], points[-1])
        if d > dmax:
            index, dmax = i, d
    if dmax > tolerance:
        left = douglas_peucker(points[: index + 1], tolerance)
        right = douglas_peucker(points[index:], tolerance)
        return left[:-1] + right
    return [points[0], points[-1]]


def polygon_area(ring):
    area = sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(ring, ring[1:]))
    return abs(area) / 2


def extract_exterior_rings(geometry):
    gtype = geometry["type"]
    coords = geometry["coordinates"]
    polys = [coords] if gtype == "Polygon" else coords if gtype == "MultiPolygon" else []
    return [poly[0] for poly in polys]  # 구멍(내부 고리)은 배경용 실루엣에서 무시한다.


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tolerance", type=float, default=1.5, help="투영 좌표계 픽셀 단위 단순화 허용 오차")
    parser.add_argument("--min-area", type=float, default=3.0, help="이보다 작은 섬(투영 좌표계 제곱픽셀)은 버림")
    args = parser.parse_args()

    print(f"다운로드 중: {SOURCE_URL}")
    with urllib.request.urlopen(SOURCE_URL) as res:
        data = json.load(res)

    project = make_projector()
    rings = []
    for feature in data["features"]:
        for ring in extract_exterior_rings(feature["geometry"]):
            rings.append([project(lng, lat) for lng, lat in ring])

    simplified = []
    for ring in rings:
        simple = douglas_peucker(ring, args.tolerance)
        if polygon_area(simple) >= args.min_area:
            simplified.append(simple)

    OUTPUT_PATH.write_text(json.dumps(simplified, separators=(",", ":")), encoding="utf-8")
    print(f"{len(rings)}개 고리 -> {len(simplified)}개로 저장: {OUTPUT_PATH} ({OUTPUT_PATH.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
