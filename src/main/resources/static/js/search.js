// F2(역 검색): docs/06-search-ui.md 2장 — 디바운스 입력, 자동완성 드롭다운,
// 키보드 내비게이션(↑/↓/Enter/Esc), 결과 없음/에러 처리.
// 검색창은 기본적으로 숨겨져 있고, 헤더의 검색 버튼을 눌러야 열린다.
import { searchStations } from "./api.js";
import { getState, setState, subscribe } from "./state.js";

const DEBOUNCE_MS = 200;
const RESULT_LIMIT = 10;
const BADGE_LIMIT = 3;

const searchToggle = document.getElementById("search-toggle");
const searchBox = document.getElementById("search-box");
const input = document.getElementById("search-input");
const resultsList = document.getElementById("search-results");

let debounceTimer = null;
let currentResults = [];
let activeIndex = -1;

searchToggle.addEventListener("click", (event) => {
  event.stopPropagation();
  setState({ searchOpen: !getState().searchOpen });
});

subscribe((state) => {
  searchBox.hidden = !state.searchOpen;
  if (state.searchOpen) {
    input.focus();
  } else if (input.value) {
    input.value = "";
    closeResults();
  }
});

input.addEventListener("input", () => {
  const query = input.value.trim();
  clearTimeout(debounceTimer);
  if (query.length < 1) {
    closeResults();
    return;
  }
  debounceTimer = setTimeout(() => runSearch(query), DEBOUNCE_MS);
});

input.addEventListener("keydown", (event) => {
  if (resultsList.hidden) {
    return;
  }
  if (event.key === "ArrowDown") {
    event.preventDefault();
    moveActive(1);
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    moveActive(-1);
  } else if (event.key === "Enter") {
    event.preventDefault();
    if (activeIndex >= 0 && currentResults[activeIndex]) {
      selectStation(currentResults[activeIndex]);
    }
  } else if (event.key === "Escape") {
    setState({ searchOpen: false });
  }
});

document.addEventListener("click", (event) => {
  if (event.target.closest("#search-box") || event.target.closest("#search-toggle")) {
    return;
  }
  setState({ searchOpen: false });
});

async function runSearch(query) {
  try {
    const res = await searchStations(query, RESULT_LIMIT);
    currentResults = res.items;
    renderResults();
  } catch (err) {
    console.error("[railroad] 역 검색 실패:", err);
    renderError();
  }
}

function renderResults() {
  activeIndex = -1;
  resultsList.innerHTML = "";

  if (currentResults.length === 0) {
    resultsList.appendChild(buildMessageItem("검색 결과가 없습니다", "search-empty"));
  } else {
    currentResults.forEach((item, index) => {
      resultsList.appendChild(buildResultItem(item, index));
    });
  }
  resultsList.hidden = false;
}

function renderError() {
  resultsList.innerHTML = "";
  resultsList.appendChild(buildMessageItem("검색 중 오류가 발생했습니다", "search-error"));
  resultsList.hidden = false;
}

function buildMessageItem(text, className) {
  const li = document.createElement("li");
  li.className = className;
  li.textContent = text;
  return li;
}

function buildResultItem(item, index) {
  const li = document.createElement("li");
  li.className = "search-result-item";
  li.id = `search-result-${index}`;
  li.setAttribute("role", "option");

  const name = document.createElement("span");
  name.className = "search-result-name";
  name.textContent = item.name;
  li.appendChild(name);

  const badges = document.createElement("span");
  badges.className = "search-result-badges";
  item.lineNames.slice(0, BADGE_LIMIT).forEach((lineName) => {
    badges.appendChild(buildBadge(lineName, "line-badge"));
  });
  if (item.lineNames.length > BADGE_LIMIT) {
    badges.appendChild(buildBadge(`+${item.lineNames.length - BADGE_LIMIT}`, "line-badge line-badge-more"));
  }
  li.appendChild(badges);

  li.addEventListener("mouseenter", () => setActive(index));
  li.addEventListener("click", () => selectStation(item));
  return li;
}

function buildBadge(text, className) {
  const span = document.createElement("span");
  span.className = className;
  span.textContent = text;
  return span;
}

function moveActive(delta) {
  const items = resultsList.querySelectorAll(".search-result-item");
  if (items.length === 0) {
    return;
  }
  const nextIndex = (activeIndex + delta + items.length) % items.length;
  setActive(nextIndex);
  items[nextIndex].scrollIntoView({ block: "nearest" });
}

function setActive(index) {
  const items = resultsList.querySelectorAll(".search-result-item");
  if (activeIndex >= 0 && items[activeIndex]) {
    items[activeIndex].classList.remove("active");
  }
  activeIndex = index;
  if (items[activeIndex]) {
    items[activeIndex].classList.add("active");
  }
}

function selectStation(item) {
  setState({
    selectedStationId: item.stationId,
    selectedLineId: null,
    detailView: { type: "station", id: item.stationId },
    searchOpen: false,
  });
}

function closeResults() {
  resultsList.hidden = true;
  resultsList.innerHTML = "";
  currentResults = [];
  activeIndex = -1;
}
