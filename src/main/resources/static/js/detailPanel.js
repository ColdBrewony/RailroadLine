// F3(역 상세 정보): docs/06-search-ui.md 3장 — 소속 노선마다 카드 하나,
// 각 카드에 이전/다음역과 구간거리. 이전/다음역 클릭 시 그 역으로 연쇄 이동.
import { getStationDetail } from "./api.js";
import { setState, subscribe } from "./state.js";

const STATION_TYPE_LABELS = {
  MANAGED: "관리역",
  STAFFED: "직원배치역",
  ENTRUSTED: "위탁역",
  FREIGHT: "화물취급역",
  UNMANNED: "무인역",
  SIGNAL_YARD: "신호장",
  SIGNAL_STATION: "신호소",
  TEMPORARY_PLATFORM: "임시승강장",
};

const LINE_STATUS_LABELS = {
  CLOSED: "폐선",
  SUSPENDED: "여객중지",
  UNBUILT: "미개통",
};

const panel = document.getElementById("station-detail-panel");
const closeBtn = document.getElementById("detail-close");
const content = document.getElementById("detail-content");

closeBtn.addEventListener("click", () => {
  setState({ selectedStationId: null });
});

let currentRequestId = 0;

subscribe((state) => {
  if (state.selectedStationId == null) {
    panel.hidden = true;
    content.innerHTML = "";
    return;
  }
  panel.hidden = false;
  loadDetail(state.selectedStationId);
});

async function loadDetail(stationId) {
  const requestId = ++currentRequestId;
  renderLoading();
  try {
    const detail = await getStationDetail(stationId);
    if (requestId !== currentRequestId) {
      return; // 더 최근 요청이 있었으면 이 응답은 버린다.
    }
    renderDetail(detail);
  } catch (err) {
    if (requestId !== currentRequestId) {
      return;
    }
    console.error("[railroad] 역 상세 조회 실패:", err);
    renderError(stationId);
  }
}

function renderLoading() {
  content.innerHTML = "";
  const p = document.createElement("p");
  p.className = "detail-loading";
  p.textContent = "불러오는 중...";
  content.appendChild(p);
}

function renderError(stationId) {
  content.innerHTML = "";
  const p = document.createElement("p");
  p.className = "detail-error";
  p.textContent = "정보를 불러오지 못했습니다.";
  content.appendChild(p);

  const retry = document.createElement("button");
  retry.type = "button";
  retry.className = "detail-retry";
  retry.textContent = "다시 시도";
  retry.addEventListener("click", () => loadDetail(stationId));
  content.appendChild(retry);
}

function renderDetail(detail) {
  content.innerHTML = "";
  content.appendChild(buildHeader(detail));
  detail.lines.forEach((line) => content.appendChild(buildLineCard(line)));
}

function buildHeader(detail) {
  const header = document.createElement("div");
  header.className = "detail-header";

  const title = document.createElement("h2");
  title.textContent = detail.name;
  header.appendChild(title);

  const meta = document.createElement("div");
  meta.className = "detail-meta";
  const parts = [];
  if (detail.stationType) {
    parts.push(STATION_TYPE_LABELS[detail.stationType] || detail.stationType);
  }
  if (detail.isKtxStop) {
    parts.push("KTX 정차");
  }
  meta.textContent = parts.length > 0 ? parts.join(" · ") : "역 종류 정보 없음";
  header.appendChild(meta);

  return header;
}

function buildLineCard(line) {
  const card = document.createElement("section");
  card.className = "line-card";

  const title = document.createElement("h3");
  title.className = "line-card-title";
  title.textContent = line.segmentLabel ? `${line.lineName} (${line.segmentLabel})` : line.lineName;
  if (line.lineStatus && line.lineStatus !== "OPERATING") {
    const badge = document.createElement("span");
    badge.className = "line-status-badge";
    badge.textContent = LINE_STATUS_LABELS[line.lineStatus] || line.lineStatus;
    title.appendChild(badge);
  }
  card.appendChild(title);

  const row = document.createElement("div");
  row.className = "line-card-row";

  if (line.prevStation) {
    row.appendChild(buildNeighborLink(line.prevStation));
    row.appendChild(buildDistance(line.prevStation.distanceKm));
  } else {
    row.appendChild(buildTag("기점"));
  }

  const current = document.createElement("span");
  current.className = "line-card-current";
  current.textContent = "현재역";
  row.appendChild(current);

  if (line.nextStation) {
    row.appendChild(buildDistance(line.nextStation.distanceKm));
    row.appendChild(buildNeighborLink(line.nextStation));
  } else {
    row.appendChild(buildTag("종점"));
  }

  card.appendChild(row);
  return card;
}

function buildNeighborLink(neighbor) {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "neighbor-link";
  btn.textContent = neighbor.name;
  btn.addEventListener("click", () => setState({ selectedStationId: neighbor.stationId }));
  return btn;
}

function buildDistance(km) {
  const span = document.createElement("span");
  span.className = "line-card-distance";
  span.textContent = km != null ? `${formatKm(km)}km` : "-";
  return span;
}

function buildTag(text) {
  const span = document.createElement("span");
  span.className = "line-card-tag";
  span.textContent = `(${text})`;
  return span;
}

function formatKm(km) {
  const num = typeof km === "number" ? km : parseFloat(km);
  return Number.isFinite(num) ? num.toFixed(1) : km;
}
