// F3(역 상세 정보) + F4(노선 상세 정보): docs/06-search-ui.md 3장.
// 지도/사이드바에서 역을 클릭하면 소속 노선마다 카드 하나(이전/다음역·구간거리),
// 노선을 클릭하면 그 노선의 전체 역 목록을 이 패널에 보여준다. 여러 노선이 겹치는
// 구간을 클릭했을 때는 어느 노선인지 고르는 목록을 먼저 보여준다.
import { getStationDetail, getLineDetail } from "./api.js";
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

// 무엇을 보고 있었든, 닫으면 역/노선 선택과 지도 강조를 전부 지우고 전체 노선망으로 돌아간다.
closeBtn.addEventListener("click", () => {
  setState({ selectedStationId: null, selectedLineId: null, detailView: null });
});

let currentRequestId = 0;
let currentView = null;

subscribe((state) => {
  const view = state.detailView;
  if (view === currentView) {
    return; // 다른 상태(패널과 무관한 것)만 바뀐 경우 다시 그리지 않는다.
  }
  currentView = view;
  if (!view) {
    panel.hidden = true;
    content.innerHTML = "";
    return;
  }
  panel.hidden = false;
  if (view.type === "station") {
    loadStationDetail(view.id);
  } else if (view.type === "line") {
    loadLineDetail(view.id);
  } else if (view.type === "segment") {
    renderSegmentChooser(view.segmentLines);
  }
});

async function loadStationDetail(stationId) {
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
    renderError(() => loadStationDetail(stationId));
  }
}

async function loadLineDetail(lineId) {
  const requestId = ++currentRequestId;
  renderLoading();
  try {
    const detail = await getLineDetail(lineId);
    if (requestId !== currentRequestId) {
      return;
    }
    renderLineDetail(detail);
  } catch (err) {
    if (requestId !== currentRequestId) {
      return;
    }
    console.error("[railroad] 노선 상세 조회 실패:", err);
    renderError(() => loadLineDetail(lineId));
  }
}

function renderLoading() {
  content.innerHTML = "";
  const p = document.createElement("p");
  p.className = "detail-loading";
  p.textContent = "불러오는 중...";
  content.appendChild(p);
}

function renderError(retryFn) {
  content.innerHTML = "";
  const p = document.createElement("p");
  p.className = "detail-error";
  p.textContent = "정보를 불러오지 못했습니다.";
  content.appendChild(p);

  const retry = document.createElement("button");
  retry.type = "button";
  retry.className = "detail-retry";
  retry.textContent = "다시 시도";
  retry.addEventListener("click", retryFn);
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

  const nav = document.createElement("div");
  nav.className = "station-nav";
  nav.appendChild(buildStationNavSlot(line.prevStation, "prev"));
  nav.appendChild(buildStationNavCurrent());
  nav.appendChild(buildStationNavSlot(line.nextStation, "next"));
  card.appendChild(nav);

  return card;
}

/** 가운데(현재역) 칸. 역 이름은 패널 위쪽 제목에 이미 있으니 여기서는 "현재역"만 표시한다. */
function buildStationNavCurrent() {
  const div = document.createElement("div");
  div.className = "station-nav-current";
  div.textContent = "현재역";
  return div;
}

/** 이전/다음역 칸. 역이 있으면 화살표+이름+거리를 담은 큼직한 버튼, 없으면(기점/종점) 흐린 표시. */
function buildStationNavSlot(neighbor, direction) {
  const isPrev = direction === "prev";
  if (!neighbor) {
    const placeholder = document.createElement("div");
    placeholder.className = `station-nav-btn station-nav-${direction} station-nav-placeholder`;
    placeholder.textContent = isPrev ? "기점" : "종점";
    return placeholder;
  }

  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = `station-nav-btn station-nav-${direction}`;
  btn.title = `${neighbor.name}으로 이동`;
  btn.addEventListener("click", () =>
    setState({
      selectedStationId: neighbor.stationId,
      selectedLineId: null,
      detailView: { type: "station", id: neighbor.stationId },
    })
  );

  const arrow = document.createElement("span");
  arrow.className = "station-nav-arrow";
  arrow.textContent = isPrev ? "◀" : "▶";
  arrow.setAttribute("aria-hidden", "true");

  const info = document.createElement("span");
  info.className = "station-nav-info";
  const name = document.createElement("span");
  name.className = "station-nav-name";
  name.textContent = neighbor.name;
  info.appendChild(name);
  if (neighbor.distanceKm != null) {
    const km = document.createElement("span");
    km.className = "station-nav-km";
    km.textContent = `${formatKm(neighbor.distanceKm)}km`;
    info.appendChild(km);
  }

  if (isPrev) {
    btn.appendChild(arrow);
    btn.appendChild(info);
  } else {
    btn.appendChild(info);
    btn.appendChild(arrow);
  }
  return btn;
}

/** F4(노선 상세): 노선 개요 + 순서대로 나열한 전체 역 목록. */
function renderLineDetail(detail) {
  content.innerHTML = "";
  content.appendChild(buildLineHeader(detail));
  content.appendChild(buildLineStationList(detail.stations));
}

function buildLineHeader(detail) {
  const header = document.createElement("div");
  header.className = "detail-header";

  const title = document.createElement("h2");
  title.textContent = detail.segmentLabel ? `${detail.name} (${detail.segmentLabel})` : detail.name;
  if (detail.status && detail.status !== "OPERATING") {
    const badge = document.createElement("span");
    badge.className = "line-status-badge";
    badge.textContent = LINE_STATUS_LABELS[detail.status] || detail.status;
    title.appendChild(badge);
  }
  header.appendChild(title);

  const meta = document.createElement("div");
  meta.className = "detail-meta";
  const parts = [];
  if (detail.regionNames && detail.regionNames.length > 0) {
    parts.push(detail.regionNames.join(", "));
  }
  if (detail.totalDistanceKm != null) {
    parts.push(`총 ${formatKm(detail.totalDistanceKm)}km`);
  }
  parts.push(`${detail.stations.length}개 역`);
  meta.textContent = parts.join(" · ");
  header.appendChild(meta);

  return header;
}

function buildLineStationList(stations) {
  const list = document.createElement("ol");
  list.className = "line-detail-station-list";
  stations.forEach((station) => list.appendChild(buildLineStationItem(station)));
  return list;
}

function buildLineStationItem(station) {
  const li = document.createElement("li");
  li.className = "line-detail-station-item" + (station.isTransfer ? " line-detail-station-transfer" : "");

  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "line-detail-station-link";
  btn.textContent = station.name;
  btn.addEventListener("click", () =>
    setState({
      selectedStationId: station.stationId,
      selectedLineId: null,
      detailView: { type: "station", id: station.stationId },
    })
  );
  li.appendChild(btn);

  const km = document.createElement("span");
  km.className = "line-card-distance";
  km.textContent = station.cumulativeKm != null ? `${formatKm(station.cumulativeKm)}km` : "-";
  li.appendChild(km);

  return li;
}

/** 여러 노선이 겹치는 구간을 클릭했을 때: 어느 노선을 볼지 고르는 목록. */
function renderSegmentChooser(segmentLines) {
  content.innerHTML = "";

  const header = document.createElement("div");
  header.className = "detail-header";
  const title = document.createElement("h2");
  title.textContent = "이 구간을 지나는 노선";
  header.appendChild(title);
  const meta = document.createElement("div");
  meta.className = "detail-meta";
  meta.textContent = "노선을 선택하면 해당 노선의 경로와 상세 정보를 보여줍니다.";
  header.appendChild(meta);
  content.appendChild(header);

  const list = document.createElement("ul");
  list.className = "segment-chooser-list";
  segmentLines.forEach((line) => {
    const li = document.createElement("li");
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "segment-chooser-item";
    btn.textContent = line.name;
    btn.addEventListener("click", () =>
      setState({
        selectedLineId: line.lineId,
        selectedStationId: null,
        detailView: { type: "line", id: line.lineId },
      })
    );
    li.appendChild(btn);
    list.appendChild(li);
  });
  content.appendChild(list);
}

function formatKm(km) {
  const num = typeof km === "number" ? km : parseFloat(km);
  return Number.isFinite(num) ? num.toFixed(1) : km;
}
