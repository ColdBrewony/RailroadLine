// F4(노선 목록): docs/06-search-ui.md 4장.
// 원안은 LineCategory 필터 탭이었으나, 데이터 모델 v2에서 Line.category가
// 제거되어(실수집 안 됨) 대신 노선명 필터 입력 + "폐선/중지 포함" 토글로 대체한다.
import { getLines } from "./api.js";
import { getState, setState, subscribe } from "./state.js";

const STATUS_LABELS = {
  CLOSED: "폐선",
  SUSPENDED: "여객중지",
  UNBUILT: "미개통",
};

const tabsContainer = document.getElementById("line-list-tabs");
const listEl = document.getElementById("line-list");

const filterInput = document.createElement("input");
filterInput.type = "text";
filterInput.id = "line-filter-input";
filterInput.placeholder = "노선명 필터...";
tabsContainer.appendChild(filterInput);

const includeAllLabel = document.createElement("label");
includeAllLabel.className = "line-filter-toggle";
const includeAllCheckbox = document.createElement("input");
includeAllCheckbox.type = "checkbox";
includeAllLabel.appendChild(includeAllCheckbox);
includeAllLabel.appendChild(document.createTextNode("폐선/중지 포함"));
tabsContainer.appendChild(includeAllLabel);

let debounceTimer = null;
let currentLines = [];

filterInput.addEventListener("input", () => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(loadLines, 200);
});
includeAllCheckbox.addEventListener("change", loadLines);

async function loadLines() {
  try {
    const res = await getLines(filterInput.value.trim(), includeAllCheckbox.checked);
    currentLines = res.items;
    renderList();
  } catch (err) {
    console.error("[railroad] 노선 목록 조회 실패:", err);
    renderError();
  }
}

function renderList() {
  listEl.innerHTML = "";
  if (currentLines.length === 0) {
    listEl.appendChild(buildMessageItem("조건에 맞는 노선이 없습니다", "line-list-empty"));
    return;
  }
  const selectedLineId = getState().selectedLineId;
  currentLines.forEach((line) => {
    listEl.appendChild(buildLineItem(line, line.lineId === selectedLineId));
  });
}

function renderError() {
  listEl.innerHTML = "";
  listEl.appendChild(buildMessageItem("노선 목록을 불러오지 못했습니다", "line-list-error"));
}

function buildMessageItem(text, className) {
  const li = document.createElement("li");
  li.className = className;
  li.textContent = text;
  return li;
}

function buildLineItem(line, active) {
  const li = document.createElement("li");
  li.className = "line-list-item" + (active ? " active" : "");

  const main = document.createElement("div");
  main.className = "line-list-main";

  const name = document.createElement("span");
  name.className = "line-list-name";
  name.textContent = line.segmentLabel ? `${line.name} (${line.segmentLabel})` : line.name;
  main.appendChild(name);

  if (line.regionNames && line.regionNames.length > 0) {
    const region = document.createElement("span");
    region.className = "line-list-region";
    region.textContent = line.regionNames.join(", ");
    main.appendChild(region);
  }
  li.appendChild(main);

  if (line.status && line.status !== "OPERATING") {
    const badge = document.createElement("span");
    badge.className = "line-status-badge";
    badge.textContent = STATUS_LABELS[line.status] || line.status;
    li.appendChild(badge);
  }

  const distance = document.createElement("span");
  distance.className = "line-list-distance";
  distance.textContent = line.totalDistanceKm != null ? `${formatKm(line.totalDistanceKm)}km` : "-";
  li.appendChild(distance);

  li.addEventListener("click", () => {
    const isActive = getState().selectedLineId === line.lineId;
    setState({ selectedLineId: isActive ? null : line.lineId, sidebarOpen: false });
  });

  return li;
}

function formatKm(km) {
  const num = typeof km === "number" ? km : parseFloat(km);
  return Number.isFinite(num) ? num.toFixed(1) : km;
}

// selectedLineId만 바뀐 경우 전체 재조회 없이 활성 표시만 갱신한다.
subscribe((state) => {
  const items = listEl.querySelectorAll(".line-list-item");
  items.forEach((item, index) => {
    const line = currentLines[index];
    if (line) {
      item.classList.toggle("active", line.lineId === state.selectedLineId);
    }
  });
});

loadLines();
