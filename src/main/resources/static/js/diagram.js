// F1/F5(노선도 시각화, Stage A): docs/05-frontend-design.md 2.1장.
// 선택된 노선의 역을 균등 간격 일직선으로 자동 배치해 SVG로 그린다.
// diagram_x/y(Phase 4 수동/알고리즘 배치, Stage B/C)가 아직 없어도 항상 동작한다.
import { getLineDetail, getStationDetail } from "./api.js";
import { getState, setState, subscribe } from "./state.js";

const SVG_NS = "http://www.w3.org/2000/svg";
const VIEW_W = 1000;
const VIEW_H = 600;
const STATION_SPACING = 80;
const PADDING = 60;
const TRANSFER_RADIUS = 7;
const NORMAL_RADIUS = 4;
const MIN_SCALE = 0.3;
const MAX_SCALE = 4;

const svg = document.getElementById("diagram-svg");
svg.setAttribute("viewBox", `0 0 ${VIEW_W} ${VIEW_H}`);

const content = document.createElementNS(SVG_NS, "g");
content.id = "diagram-content";
svg.appendChild(content);

let transform = { scale: 1, tx: VIEW_W / 2, ty: VIEW_H / 2 };
let currentRequestId = 0;
let currentLineId = null;
let stationNodes = new Map(); // stationId -> circle element (현재 그려진 노선 기준)

applyTransform();
renderPlaceholder();
setupPanZoom();

subscribe((state) => {
  if (state.selectedLineId !== currentLineId) {
    currentLineId = state.selectedLineId;
    if (currentLineId == null) {
      renderPlaceholder();
    } else {
      loadLine(currentLineId);
    }
  }
  highlightSelectedStation(state.selectedStationId);

  // 노선 없이 역만 선택된 경우(검색 등), 그 역이 속한 첫 노선을 자동으로 펼쳐 보여준다.
  if (state.selectedStationId != null && state.selectedLineId == null) {
    autoSelectLineForStation(state.selectedStationId);
  }
});

async function autoSelectLineForStation(stationId) {
  try {
    const detail = await getStationDetail(stationId);
    // 그 사이 다른 역/노선이 선택됐다면 무시
    if (getState().selectedStationId !== stationId || getState().selectedLineId != null) {
      return;
    }
    if (detail.lines.length > 0) {
      setState({ selectedLineId: detail.lines[0].lineId });
    }
  } catch (err) {
    console.error("[railroad] 역의 소속 노선 조회 실패:", err);
  }
}

async function loadLine(lineId) {
  const requestId = ++currentRequestId;
  renderLoading();
  try {
    const detail = await getLineDetail(lineId);
    if (requestId !== currentRequestId) {
      return;
    }
    renderLine(detail);
    highlightSelectedStation(getState().selectedStationId);
  } catch (err) {
    if (requestId !== currentRequestId) {
      return;
    }
    console.error("[railroad] 노선도 조회 실패:", err);
    renderError();
  }
}

function clearContent() {
  content.innerHTML = "";
  stationNodes = new Map();
}

function renderPlaceholder() {
  clearContent();
  transform = { scale: 1, tx: VIEW_W / 2, ty: VIEW_H / 2 };
  applyTransform();
  content.appendChild(buildText(0, 0, "왼쪽 노선 목록이나 검색에서 역/노선을 선택하세요", "diagram-placeholder"));
}

function renderLoading() {
  clearContent();
  content.appendChild(buildText(0, 0, "노선도를 불러오는 중...", "diagram-placeholder"));
}

function renderError() {
  clearContent();
  content.appendChild(buildText(0, -10, "노선도를 불러오지 못했습니다.", "diagram-placeholder"));
  content.appendChild(buildText(0, 16, "(노선을 다시 선택하면 재시도합니다)", "diagram-placeholder-sub"));
}

function renderLine(detail) {
  clearContent();

  const stations = detail.stations;
  const n = stations.length;
  const totalWidth = Math.max(0, n - 1) * STATION_SPACING;
  const startX = -totalWidth / 2;
  const color = colorForLine(detail.lineId);

  if (n > 0) {
    const track = document.createElementNS(SVG_NS, "line");
    track.setAttribute("x1", startX);
    track.setAttribute("y1", 0);
    track.setAttribute("x2", startX + totalWidth);
    track.setAttribute("y2", 0);
    track.setAttribute("stroke", color);
    track.setAttribute("stroke-width", 4);
    track.setAttribute("stroke-linecap", "round");
    content.appendChild(track);
  }

  stations.forEach((station, index) => {
    const x = startX + index * STATION_SPACING;
    const { group, circle } = buildStationNode(station, x, color);
    content.appendChild(group);
    stationNodes.set(station.stationId, circle);
  });

  const titleText = detail.segmentLabel ? `${detail.name} (${detail.segmentLabel})` : detail.name;
  content.appendChild(buildText(startX, -32, titleText, "diagram-line-title", "start"));

  fitToView(totalWidth);
}

function buildStationNode(station, x, color) {
  const group = document.createElementNS(SVG_NS, "g");
  group.setAttribute("class", "diagram-station");
  group.setAttribute("transform", `translate(${x} 0)`);

  const circle = document.createElementNS(SVG_NS, "circle");
  const isTransfer = !!station.isTransfer;
  circle.setAttribute("r", isTransfer ? TRANSFER_RADIUS : NORMAL_RADIUS);
  circle.setAttribute("fill", isTransfer ? "#ffffff" : color);
  circle.setAttribute("stroke", color);
  circle.setAttribute("stroke-width", isTransfer ? 3 : 1);
  group.appendChild(circle);

  const title = document.createElementNS(SVG_NS, "title");
  title.textContent = station.name;
  group.appendChild(title);

  const label = document.createElementNS(SVG_NS, "text");
  label.textContent = station.name;
  label.setAttribute("class", "diagram-station-label");
  label.setAttribute("y", -12);
  label.setAttribute("text-anchor", "middle");
  group.appendChild(label);

  group.addEventListener("click", (event) => {
    event.stopPropagation();
    setState({ selectedStationId: station.stationId });
  });

  return { group, circle };
}

function highlightSelectedStation(stationId) {
  stationNodes.forEach((circle, id) => {
    circle.classList.toggle("diagram-station-active", id === stationId);
  });
}

function buildText(x, y, text, className, anchor = "middle") {
  const el = document.createElementNS(SVG_NS, "text");
  el.setAttribute("x", x);
  el.setAttribute("y", y);
  el.setAttribute("text-anchor", anchor);
  el.setAttribute("class", className);
  el.textContent = text;
  return el;
}

function colorForLine(lineId) {
  const hue = (lineId * 47) % 360;
  return `hsl(${hue}, 62%, 40%)`;
}

function fitToView(totalWidth) {
  const scale = clamp(Math.min(1.2, (VIEW_W - PADDING * 2) / Math.max(1, totalWidth)), MIN_SCALE, MAX_SCALE);
  transform = { scale, tx: VIEW_W / 2, ty: VIEW_H / 2 };
  applyTransform();
}

function applyTransform() {
  content.setAttribute("transform", `translate(${transform.tx} ${transform.ty}) scale(${transform.scale})`);
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function setupPanZoom() {
  let dragStart = null;

  svg.addEventListener("pointerdown", (event) => {
    dragStart = { x: event.clientX, y: event.clientY, tx: transform.tx, ty: transform.ty };
    svg.setPointerCapture(event.pointerId);
  });

  svg.addEventListener("pointermove", (event) => {
    if (!dragStart) {
      return;
    }
    const rect = svg.getBoundingClientRect();
    const scaleX = VIEW_W / rect.width;
    const scaleY = VIEW_H / rect.height;
    transform.tx = dragStart.tx + (event.clientX - dragStart.x) * scaleX;
    transform.ty = dragStart.ty + (event.clientY - dragStart.y) * scaleY;
    applyTransform();
  });

  const endDrag = () => {
    dragStart = null;
  };
  svg.addEventListener("pointerup", endDrag);
  svg.addEventListener("pointercancel", endDrag);

  svg.addEventListener(
    "wheel",
    (event) => {
      event.preventDefault();
      const rect = svg.getBoundingClientRect();
      const scaleX = VIEW_W / rect.width;
      const scaleY = VIEW_H / rect.height;
      const pointerX = (event.clientX - rect.left) * scaleX;
      const pointerY = (event.clientY - rect.top) * scaleY;

      const factor = event.deltaY < 0 ? 1.1 : 1 / 1.1;
      const newScale = clamp(transform.scale * factor, MIN_SCALE, MAX_SCALE);
      const ratio = newScale / transform.scale;

      transform.tx = pointerX - (pointerX - transform.tx) * ratio;
      transform.ty = pointerY - (pointerY - transform.ty) * ratio;
      transform.scale = newScale;
      applyTransform();
    },
    { passive: false }
  );
}
