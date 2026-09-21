import { getState, setState, subscribe } from "./state.js";
import "./search.js?v=20260921-detail6";
import "./detailPanel.js?v=20260921-detail6";
import "./lineList.js?v=20260921-detail6";
import "./diagram.js?v=20260921-detail6";

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

subscribe((state) => {
  sidebar.classList.toggle("open", state.sidebarOpen);
  backdrop.classList.toggle("open", state.sidebarOpen);
  backdrop.hidden = !state.sidebarOpen;
  legend.hidden = !state.legendOpen;
});
