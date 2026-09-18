// 백엔드 REST API(docs/04-api-design.md) fetch 래퍼.
const API_BASE = "/api";

async function apiGet(path) {
  const res = await fetch(API_BASE + path);
  if (!res.ok) {
    let body = null;
    try {
      body = await res.json();
    } catch (e) {
      // 본문이 JSON이 아닐 수 있음(무시)
    }
    const message = (body && body.message) || `요청에 실패했습니다 (${res.status})`;
    const error = new Error(message);
    error.status = res.status;
    error.body = body;
    throw error;
  }
  return res.json();
}

/** F2: 역 이름 부분 일치 검색. */
export function searchStations(query, limit = 20) {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  return apiGet(`/stations/search?${params.toString()}`);
}

/** F3: 역 상세(소속 노선, 이전/다음역). */
export function getStationDetail(stationId) {
  return apiGet(`/stations/${stationId}`);
}

/** F4: 노선 목록. includeAll=true면 폐선/중지/미개통 노선도 포함. */
export function getLines(query = "", includeAll = false) {
  const params = new URLSearchParams();
  if (query) params.set("q", query);
  if (includeAll) params.set("all", "true");
  const qs = params.toString();
  return apiGet(`/lines${qs ? "?" + qs : ""}`);
}

/** F4: 노선 상세(소속 역 목록, 순서대로). */
export function getLineDetail(lineId) {
  return apiGet(`/lines/${lineId}`);
}
