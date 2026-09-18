import { getLines } from "./api.js";
import { getState, setState, subscribe } from "./state.js";
import "./search.js";

const sidebar = document.getElementById("line-list-sidebar");
const backdrop = document.getElementById("sidebar-backdrop");
const menuToggle = document.getElementById("menu-toggle");
const legend = document.getElementById("legend");
const legendToggle = document.getElementById("legend-toggle");

// 모바일 사이드바 드로어 토글
menuToggle.addEventListener("click", () => {
  setState({ sidebarOpen: !getState().sidebarOpen });
});
backdrop.addEventListener("click", () => {
  setState({ sidebarOpen: false });
});

// 범례 토글
legendToggle.addEventListener("click", () => {
  setState({ legendOpen: !getState().legendOpen });
});

let lastLoggedStationId;
subscribe((state) => {
  sidebar.classList.toggle("open", state.sidebarOpen);
  backdrop.classList.toggle("open", state.sidebarOpen);
  backdrop.hidden = !state.sidebarOpen;
  legend.hidden = !state.legendOpen;

  // 역 상세 패널(다음 단계)이 아직 없어, 선택된 역을 우선 콘솔로 확인한다.
  if (state.selectedStationId !== lastLoggedStationId) {
    lastLoggedStationId = state.selectedStationId;
    console.info("[railroad] selectedStationId =", state.selectedStationId);
  }
});

// 스켈레톤 단계 연결 확인용: 백엔드 API가 정상 응답하는지 콘솔에 로그.
// 노선 목록/역 검색을 실제 화면에 그리는 작업은 이후 단계(검색, 노선 목록, 노선도)에서 진행한다.
getLines()
  .then((res) => {
    console.info(`[railroad] API 연결 확인: 노선 ${res.total}개 수신`);
  })
  .catch((err) => {
    console.error("[railroad] API 연결 실패:", err);
  });
