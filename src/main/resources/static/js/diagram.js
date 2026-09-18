// F1(전체 노선도 시각화): docs/05-frontend-design.md.
// /api/diagram을 한 번 불러와 정상 운행 중인 모든 노선/역을 동시에 그린다(지하철도처럼
// 서로 연결된 통합 노선도). 개별 노선을 선택하면 화면 전환 없이 해당 노선만 강조하고,
// 역을 선택하면(검색/클릭) 그 지점으로 pan/zoom해서 포인트를 강조한다.
import { getDiagram } from "./api.js";
import { getState, setState, subscribe } from "./state.js";

const SVG_NS = "http://www.w3.org/2000/svg";
const VIEW_W = 1000;
const VIEW_H = 1000;
const FIT_PADDING = 40;
const TRANSFER_RADIUS = 7;
const NORMAL_RADIUS = 4;
const MIN_SCALE = 0.4;
const MAX_SCALE = 10;
const FOCUS_SCALE = 3;
const LABEL_SCALE_THRESHOLD = 2.2;

const svg = document.getElementById("diagram-svg");
const resetViewBtn = document.getElementById("reset-view");
svg.setAttribute("viewBox", `0 0 ${VIEW_W} ${VIEW_H}`);

const content = document.createElementNS(SVG_NS, "g");
content.id = "diagram-content";
svg.appendChild(content);

let transform = { scale: 1, tx: VIEW_W / 2, ty: VIEW_H / 2 };
let baseScale = 1;
let dataCenterX = 0;
let dataCenterY = 0;

let diagramData = null;
let stationIndex = new Map(); // stationId -> {stationId, name, x, y, lineIds:Set}
let lineColorById = new Map(); // lineId -> color
let stationNodeEls = new Map(); // stationId -> <g> element
let linePathEls = new Map(); // lineId -> <polyline> element

let currentStationId = null;
let currentLineId = null;

applyTransform();
renderPlaceholder("불러오는 중...");
setupPanZoom();
resetViewBtn.addEventListener("click", () => {
  setState({ selectedStationId: null, selectedLineId: null });
  resetView();
});

loadDiagram();

subscribe((state) => {
  if (state.selectedStationId !== currentStationId) {
    currentStationId = state.selectedStationId;
    highlightSelectedStation(currentStationId);
    if (currentStationId != null && diagramData) {
      focusOnStation(currentStationId);
    }
  }
  if (state.selectedLineId !== currentLineId) {
    currentLineId = state.selectedLineId;
    highlightSelectedLine(currentLineId);
  }
});

async function loadDiagram() {
  try {
    diagramData = await getDiagram();
    buildIndex();
    renderMap();
    computeBaseTransform();
    resetView();
    highlightSelectedStation(getState().selectedStationId);
    highlightSelectedLine(getState().selectedLineId);
    if (getState().selectedStationId != null) {
      focusOnStation(getState().selectedStationId);
    }
  } catch (err) {
    console.error("[railroad] 전체 노선도 조회 실패:", err);
    renderPlaceholder("노선도를 불러오지 못했습니다. (새로고침하면 재시도합니다)");
  }
}

function buildIndex() {
  stationIndex = new Map();
  lineColorById = new Map();
  diagramData.lines.forEach((line) => {
    lineColorById.set(line.lineId, line.color);
    line.stations.forEach((st) => {
      if (!stationIndex.has(st.stationId)) {
        stationIndex.set(st.stationId, {
          stationId: st.stationId,
          name: st.name,
          x: Number(st.x),
          y: Number(st.y),
          lineIds: new Set(),
        });
      }
      stationIndex.get(st.stationId).lineIds.add(line.lineId);
    });
  });
}

function renderMap() {
  content.innerHTML = "";
  stationNodeEls = new Map();
  linePathEls = new Map();

  const transferSet = new Set(diagramData.transferStationIds);

  const linesGroup = document.createElementNS(SVG_NS, "g");
  linesGroup.setAttribute("class", "diagram-lines-group");
  diagramData.lines.forEach((line) => {
    if (line.stations.length < 2) {
      return;
    }
    const points = line.stations.map((st) => `${st.x},${st.y}`).join(" ");
    const path = document.createElementNS(SVG_NS, "polyline");
    path.setAttribute("points", points);
    path.setAttribute("fill", "none");
    path.setAttribute("stroke", line.color);
    path.setAttribute("stroke-width", 3);
    path.setAttribute("stroke-linecap", "round");
    path.setAttribute("stroke-linejoin", "round");
    path.setAttribute("class", "diagram-line-path");

    const title = document.createElementNS(SVG_NS, "title");
    title.textContent = line.segmentLabel ? `${line.name} (${line.segmentLabel})` : line.name;
    path.appendChild(title);

    path.addEventListener("click", (event) => {
      event.stopPropagation();
      const isActive = getState().selectedLineId === line.lineId;
      setState({ selectedLineId: isActive ? null : line.lineId });
    });

    linesGroup.appendChild(path);
    linePathEls.set(line.lineId, path);
  });
  content.appendChild(linesGroup);

  const stationsGroup = document.createElementNS(SVG_NS, "g");
  stationsGroup.setAttribute("class", "diagram-stations-group");
  stationIndex.forEach((st) => {
    const isTransfer = transferSet.has(st.stationId);
    const group = buildStationNode(st, isTransfer);
    stationsGroup.appendChild(group);
    stationNodeEls.set(st.stationId, group);
  });
  content.appendChild(stationsGroup);
}

function buildStationNode(st, isTransfer) {
  const group = document.createElementNS(SVG_NS, "g");
  group.setAttribute("class", "diagram-station" + (isTransfer ? " diagram-station-transfer" : ""));
  group.setAttribute("transform", `translate(${st.x} ${st.y})`);

  const color = lineColorById.get(st.lineIds.values().next().value) || "#666";

  const circle = document.createElementNS(SVG_NS, "circle");
  circle.setAttribute("r", isTransfer ? TRANSFER_RADIUS : NORMAL_RADIUS);
  circle.setAttribute("fill", isTransfer ? "#ffffff" : color);
  circle.setAttribute("stroke", isTransfer ? "#333333" : color);
  circle.setAttribute("stroke-width", isTransfer ? 3 : 1);
  group.appendChild(circle);

  const title = document.createElementNS(SVG_NS, "title");
  title.textContent = st.name;
  group.appendChild(title);

  const label = document.createElementNS(SVG_NS, "text");
  label.textContent = st.name;
  label.setAttribute("class", "diagram-station-label");
  label.setAttribute("y", -12);
  label.setAttribute("text-anchor", "middle");
  group.appendChild(label);

  group.addEventListener("click", (event) => {
    event.stopPropagation();
    setState({ selectedStationId: st.stationId });
  });

  return group;
}

function highlightSelectedStation(stationId) {
  stationNodeEls.forEach((group, id) => {
    group.classList.toggle("diagram-station-active", id === stationId);
  });
}

function highlightSelectedLine(lineId) {
  linePathEls.forEach((path, id) => {
    path.classList.toggle("diagram-line-active", id === lineId);
    path.classList.toggle("diagram-line-dimmed", lineId != null && id !== lineId);
  });
  stationNodeEls.forEach((group, id) => {
    const st = stationIndex.get(id);
    const related = lineId == null || (st && st.lineIds.has(lineId));
    group.classList.toggle("diagram-station-dimmed", !related);
  });
}

function renderPlaceholder(message) {
  content.innerHTML = "";
  stationNodeEls = new Map();
  linePathEls = new Map();
  const text = document.createElementNS(SVG_NS, "text");
  text.setAttribute("x", 0);
  text.setAttribute("y", 0);
  text.setAttribute("text-anchor", "middle");
  text.setAttribute("class", "diagram-placeholder");
  text.textContent = message;
  content.appendChild(text);
}

/** 데이터 좌표계의 중심/크기를 구해, 전체가 viewBox 안에 한 번에 들어오는 기준 배율을 계산한다. */
function computeBaseTransform() {
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  stationIndex.forEach((st) => {
    minX = Math.min(minX, st.x);
    maxX = Math.max(maxX, st.x);
    minY = Math.min(minY, st.y);
    maxY = Math.max(maxY, st.y);
  });
  if (!Number.isFinite(minX)) {
    minX = maxX = minY = maxY = 0;
  }
  dataCenterX = (minX + maxX) / 2;
  dataCenterY = (minY + maxY) / 2;
  const dataW = Math.max(1, maxX - minX);
  const dataH = Math.max(1, maxY - minY);
  const availW = VIEW_W - FIT_PADDING * 2;
  const availH = VIEW_H - FIT_PADDING * 2;
  baseScale = Math.min(availW / dataW, availH / dataH);
}

/** 사용자 확대/축소·이동을 초기화하고 전체 노선도가 한 화면에 들어오게 한다. */
function resetView(animate) {
  transform = { scale: 1, tx: VIEW_W / 2, ty: VIEW_H / 2 };
  applyTransform(animate);
}

/** 특정 역이 화면 중앙에 오도록 pan/zoom한다(검색 결과 선택, 역 클릭 등). */
function focusOnStation(stationId) {
  const st = stationIndex.get(stationId);
  if (!st) {
    return;
  }
  const scale = Math.max(transform.scale, FOCUS_SCALE);
  const s = scale * baseScale;
  transform = {
    scale,
    tx: VIEW_W / 2 - s * (st.x - dataCenterX),
    ty: VIEW_H / 2 - s * (st.y - dataCenterY),
  };
  applyTransform(true);
}

function applyTransform(animate) {
  content.classList.toggle("diagram-animate", !!animate);
  const s = transform.scale * baseScale;
  content.setAttribute(
    "transform",
    `translate(${transform.tx} ${transform.ty}) scale(${s}) translate(${-dataCenterX} ${-dataCenterY})`
  );
  content.classList.toggle("show-labels", transform.scale > LABEL_SCALE_THRESHOLD);
  if (animate) {
    window.setTimeout(() => content.classList.remove("diagram-animate"), 450);
  }
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function setupPanZoom() {
  let dragStart = null;
  let dragMoved = false;
  const CLICK_THRESHOLD = 4;

  svg.addEventListener("pointerdown", (event) => {
    dragStart = { x: event.clientX, y: event.clientY, tx: transform.tx, ty: transform.ty };
    dragMoved = false;
    svg.setPointerCapture(event.pointerId);
  });

  svg.addEventListener("pointermove", (event) => {
    if (!dragStart) {
      return;
    }
    const rect = svg.getBoundingClientRect();
    const scaleX = VIEW_W / rect.width;
    const scaleY = VIEW_H / rect.height;
    const dx = (event.clientX - dragStart.x) * scaleX;
    const dy = (event.clientY - dragStart.y) * scaleY;
    if (Math.abs(event.clientX - dragStart.x) > CLICK_THRESHOLD || Math.abs(event.clientY - dragStart.y) > CLICK_THRESHOLD) {
      dragMoved = true;
    }
    transform.tx = dragStart.tx + dx;
    transform.ty = dragStart.ty + dy;
    applyTransform();
  });

  const endDrag = () => {
    dragStart = null;
  };
  svg.addEventListener("pointerup", endDrag);
  svg.addEventListener("pointercancel", endDrag);

  // 드래그 없이 빈 배경을 클릭하면 선택된 역만 해제한다(노선 강조는 유지).
  svg.addEventListener("click", (event) => {
    if (dragMoved || event.target !== svg) {
      return;
    }
    setState({ selectedStationId: null });
  });

  svg.addEventListener(
    "wheel",
    (event) => {
      event.preventDefault();
      const rect = svg.getBoundingClientRect();
      const scaleX = VIEW_W / rect.width;
      const scaleY = VIEW_H / rect.height;
      const pointerX = (event.clientX - rect.left) * scaleX;
      const pointerY = (event.clientY - rect.top) * scaleY;

      const factor = event.deltaY < 0 ? 1.15 : 1 / 1.15;
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
