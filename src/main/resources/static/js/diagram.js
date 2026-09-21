// F1(전체 노선도 시각화): docs/05-frontend-design.md.
// /api/diagram을 한 번 불러와 정상 운행 중인 모든 노선/역을 동시에 그린다(지하철도처럼
// 서로 연결된 통합 노선도). 개별 노선을 선택하면 화면 전환 없이 해당 노선만 강조하고,
// 역을 선택하면(검색/클릭) 그 지점으로 pan/zoom해서 포인트를 강조한다.
import { getDiagram } from "./api.js";
import { getState, setState, subscribe } from "./state.js";

const OUTLINE_URL = "/data/korea-outline.json";

const SVG_NS = "http://www.w3.org/2000/svg";
const VIEW_W = 1000;
const VIEW_H = 1000;
const FIT_PADDING = 40;
const TRANSFER_RADIUS = 7;
const NORMAL_RADIUS = 4;
const MIN_SCALE = 0.4;
// 이제 역을 실제 좌표에 그대로 배치하고(디클러터링 없음) 지도 전체 범위를 기준으로 축척을
// 잡기 때문에, 서울처럼 역이 촘촘한 구간은 아주 깊이 확대해야 점들이 서로 떨어져 보인다.
const MAX_SCALE = 80;
const FOCUS_SCALE = 15;
const LABEL_SCALE_THRESHOLD = 2.2;
const WHEEL_ZOOM_FACTOR = 1.2;

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
let stationNodeEls = new Map(); // stationId -> <g> element
let lineIndex = new Map(); // lineId -> 원래 노선 경로(선택할 때만 표시)
let networkGroup = null;
let selectedLinePath = null; // 기본 구간 위에 표시하는 단 하나의 선택 경로

let outlineRings = null; // [[ [x,y], ... ], ...] (역 좌표와 동일한 투영 좌표계)
let outlineGroup = null; // renderMap()이 content를 비울 때마다 다시 맨 앞에 붙이는 배경 레이어

// 역이 실제 좌표에 그대로 놓이다 보니(디클러터링 없음) 확대 배율에 따라 점/글자 크기를
// 화면 픽셀 기준으로 다시 계산해야 한다(내용 좌표계 그대로면 확대할수록 원이 거대해짐).
let stationCircleEls = []; // [{circle, isTransfer}]
let lastSizedScale = null;
const NORMAL_RADIUS_PX = 4;
const TRANSFER_RADIUS_PX = 7;
const LABEL_FONT_PX = 11;
const MIN_CONTENT_RADIUS = 0.15;
const MAX_CONTENT_RADIUS = 24;
const MIN_CONTENT_FONT = 0.4;
const MAX_CONTENT_FONT = 60;

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
  let stationChanged = false;
  if (state.selectedStationId !== currentStationId) {
    currentStationId = state.selectedStationId;
    stationChanged = true;
    highlightSelectedStation(currentStationId);
    if (currentStationId != null && diagramData) {
      focusOnStation(currentStationId);
    }
  }
  if (state.selectedLineId !== currentLineId || stationChanged) {
    currentLineId = state.selectedLineId;
    highlightSelectedLine(currentLineId);
  }
});

async function loadDiagram() {
  try {
    const [diagram, outline] = await Promise.all([getDiagram(), loadOutline()]);
    diagramData = diagram;
    outlineRings = outline;
    buildIndex();
    if (outlineRings) {
      outlineGroup = buildOutlineGroup(outlineRings);
    }
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

/** 배경 지도 윤곽선(정적 자원). 못 불러와도 역/노선 렌더링 자체는 계속되게 실패를 흡수한다. */
async function loadOutline() {
  try {
    const res = await fetch(OUTLINE_URL);
    if (!res.ok) {
      return null;
    }
    return await res.json();
  } catch (err) {
    console.error("[railroad] 배경 지도 윤곽선을 불러오지 못했습니다:", err);
    return null;
  }
}

function buildOutlineGroup(rings) {
  const group = document.createElementNS(SVG_NS, "g");
  group.setAttribute("class", "map-outline-group");
  rings.forEach((ring) => {
    const points = ring.map(([x, y]) => `${x},${y}`).join(" ");
    const polygon = document.createElementNS(SVG_NS, "polygon");
    polygon.setAttribute("points", points);
    polygon.setAttribute("class", "map-outline-ring");
    group.appendChild(polygon);
  });
  return group;
}

function buildIndex() {
  stationIndex = new Map();
  lineIndex = new Map();
  diagramData.lines.forEach((line) => {
    lineIndex.set(line.lineId, line);
    line.stations.forEach((st) => {
      if (!stationIndex.has(st.stationId)) {
        stationIndex.set(st.stationId, {
          stationId: st.stationId,
          name: st.name,
          x: Number(st.x),
          y: Number(st.y),
          coordinateVerified: st.coordinateVerified !== false,
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
  networkGroup = null;
  selectedLinePath = null;
  lastSizedScale = null; // 새로 그린 원/글자 엘리먼트는 아직 화면 픽셀 크기를 못 받았으니 강제로 재계산

  if (outlineGroup) {
    content.appendChild(outlineGroup);
  }

  const transferSet = new Set(diagramData.transferStationIds);

  // 같은 두 역 사이의 연결은 진행 방향과 무관하게 하나만 그린다.
  // 좌표가 가깝다는 이유로 다른 선로나 단순 교차 지점을 합치지는 않는다.
  const segments = new Map();
  diagramData.lines.forEach((line) => {
    for (let i = 1; i < line.stations.length; i++) {
      const from = line.stations[i - 1];
      const to = line.stations[i];
      if (from.stationId === to.stationId) {
        continue;
      }
      const key = JSON.stringify([from.stationId, to.stationId].sort((a, b) => a - b));
      if (!segments.has(key)) {
        segments.set(key, { from, to, lines: new Map() });
      }
      segments.get(key).lines.set(line.lineId, line);
    }
  });

  networkGroup = document.createElementNS(SVG_NS, "g");
  networkGroup.setAttribute("class", "diagram-lines-group");
  segments.forEach(({ from, to, lines }) => {
    const shared = lines.size > 1;
    const firstLine = lines.values().next().value;
    const path = document.createElementNS(SVG_NS, "polyline");
    path.setAttribute("points", `${from.x},${from.y} ${to.x},${to.y}`);
    path.setAttribute("fill", "none");
    path.setAttribute("stroke", "#87939f");
    path.setAttribute("stroke-width", 2);
    path.setAttribute("stroke-linecap", "round");
    path.setAttribute("stroke-linejoin", "round");
    path.setAttribute("class", "diagram-line-path" + (shared ? " diagram-line-shared" : ""));

    const title = document.createElementNS(SVG_NS, "title");
    title.textContent = [...lines.values()].map(lineDisplayName).join(" · ")
      + (shared ? " — 노선 목록에서 선택해 경로를 확인하세요" : "");
    path.appendChild(title);

    if (!shared) {
      path.addEventListener("click", (event) => {
        event.stopPropagation();
        const isActive = getState().selectedLineId === firstLine.lineId;
        setState({ selectedLineId: isActive ? null : firstLine.lineId });
      });
    }
    networkGroup.appendChild(path);
  });
  content.appendChild(networkGroup);

  // 항상 기본 선보다 위, 역보다 아래에 있어 선택 순서와 무관하게 경로가 잘 보인다.
  selectedLinePath = document.createElementNS(SVG_NS, "polyline");
  selectedLinePath.setAttribute("class", "diagram-line-path diagram-line-active");
  selectedLinePath.setAttribute("fill", "none");
  selectedLinePath.setAttribute("stroke-linecap", "round");
  selectedLinePath.setAttribute("stroke-linejoin", "round");
  selectedLinePath.style.display = "none";
  selectedLinePath.addEventListener("click", (event) => {
    event.stopPropagation();
    setState({ selectedLineId: null });
  });
  content.appendChild(selectedLinePath);

  const stationsGroup = document.createElementNS(SVG_NS, "g");
  stationsGroup.setAttribute("class", "diagram-stations-group");
  stationCircleEls = [];
  stationIndex.forEach((st) => {
    const isTransfer = transferSet.has(st.stationId);
    const group = buildStationNode(st, isTransfer);
    stationsGroup.appendChild(group);
    stationNodeEls.set(st.stationId, group);
    stationCircleEls.push({
      circle: group.querySelector("circle"),
      label: group.querySelector(".diagram-station-label"),
      isTransfer,
    });
  });
  content.appendChild(stationsGroup);
}

function buildStationNode(st, isTransfer) {
  const group = document.createElementNS(SVG_NS, "g");
  group.setAttribute("class", "diagram-station" + (isTransfer ? " diagram-station-transfer" : ""));
  group.setAttribute("transform", `translate(${st.x} ${st.y})`);
  group.classList.toggle("diagram-station-estimated", !st.coordinateVerified);

  const color = "#87939f";

  const circle = document.createElementNS(SVG_NS, "circle");
  circle.setAttribute("r", isTransfer ? TRANSFER_RADIUS : NORMAL_RADIUS);
  circle.setAttribute("fill", isTransfer ? "#ffffff" : color);
  circle.setAttribute("stroke", isTransfer ? "#333333" : color);
  circle.setAttribute("stroke-width", isTransfer ? 3 : 1);
  group.appendChild(circle);

  const title = document.createElementNS(SVG_NS, "title");
  title.textContent = st.name + (st.coordinateVerified ? "" : " — 위치 미확인 (추정 배치)");
  group.appendChild(title);

  const label = document.createElementNS(SVG_NS, "text");
  label.textContent = st.name + (st.coordinateVerified ? "" : " ≈");
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
  const activeStationId = getState().selectedStationId;
  const line = lineIndex.get(lineId);
  networkGroup?.classList.toggle("diagram-line-dimmed", !!line);
  if (selectedLinePath) {
    selectedLinePath.style.display = line && line.stations.length >= 2 ? "" : "none";
    selectedLinePath.innerHTML = "";
    if (line) {
      selectedLinePath.setAttribute("points", line.stations.map((st) => `${st.x},${st.y}`).join(" "));
      selectedLinePath.setAttribute("stroke", line.color);
      const title = document.createElementNS(SVG_NS, "title");
      title.textContent = lineDisplayName(line);
      selectedLinePath.appendChild(title);
    }
  }
  stationNodeEls.forEach((group, id) => {
    const st = stationIndex.get(id);
    const circle = group.querySelector("circle");
    const onLine = line && st?.lineIds.has(lineId);
    const color = onLine ? line.color : "#87939f";
    circle.setAttribute("stroke", color);
    circle.setAttribute("fill", group.classList.contains("diagram-station-transfer") ? "#ffffff" : color);
    // 검색/클릭으로 선택된 역은 다른 노선이 강조된 상태여도 항상 보이게 한다.
    const related = !line || (st && st.lineIds.has(lineId)) || id === activeStationId;
    group.classList.toggle("diagram-station-dimmed", !related);
  });
}

function lineDisplayName(line) {
  return line.segmentLabel ? `${line.name} (${line.segmentLabel})` : line.name;
}

function renderPlaceholder(message) {
  content.innerHTML = "";
  stationNodeEls = new Map();
  networkGroup = null;
  selectedLinePath = null;
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
  // 배경 지도(제주 등 역이 없는 지역 포함)도 처음부터 화면 안에 들어오게 범위에 포함시킨다.
  if (outlineRings) {
    outlineRings.forEach((ring) => {
      ring.forEach(([x, y]) => {
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
      });
    });
  }
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
  updateStationVisualSizes(s);
  if (animate) {
    window.setTimeout(() => content.classList.remove("diagram-animate"), 450);
  }
}

/**
 * 역을 실제 좌표 그대로 그리다 보니(디클러터링 없음) 확대할수록 점/글자가 내용 좌표계 기준으로
 * 커진다. 화면상 크기를 항상 일정하게 유지하도록 현재 전체 배율(s)의 역수로 반지름/글자
 * 크기를 다시 계산한다 — 실제 지도 서비스의 마커/라벨이 줌과 무관하게 일정 크기로 보이는 것과
 * 같은 방식이다.
 */
function updateStationVisualSizes(s) {
  if (s === lastSizedScale) {
    return; // 순수 드래그(팬)처럼 배율이 안 바뀌었으면 723개 역을 매번 다시 계산하지 않는다.
  }
  lastSizedScale = s;
  const scale = Math.max(1e-6, s);
  const normalR = clamp(NORMAL_RADIUS_PX / scale, MIN_CONTENT_RADIUS, MAX_CONTENT_RADIUS);
  const transferR = clamp(TRANSFER_RADIUS_PX / scale, MIN_CONTENT_RADIUS * 1.5, MAX_CONTENT_RADIUS * 1.5);
  const fontSize = clamp(LABEL_FONT_PX / scale, MIN_CONTENT_FONT, MAX_CONTENT_FONT);
  const labelOffset = -normalR * 3;
  stationCircleEls.forEach(({circle, label, isTransfer}) => {
    circle.setAttribute("r", isTransfer ? transferR : normalR);
    if (label) {
      label.style.fontSize = `${fontSize}px`;
      label.setAttribute("y", labelOffset);
    }
  });
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

  // 드래그 없이 빈 배경(또는 배경 지도)을 클릭하면 선택된 역만 해제한다(노선 강조는 유지).
  svg.addEventListener("click", (event) => {
    const isBackground = event.target === svg || event.target.classList.contains("map-outline-ring");
    if (dragMoved || !isBackground) {
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

      const factor = event.deltaY < 0 ? WHEEL_ZOOM_FACTOR : 1 / WHEEL_ZOOM_FACTOR;
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
