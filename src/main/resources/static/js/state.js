// 화면 전역 상태 + 아주 단순한 구독/발행. 프레임워크 없이 모듈들이 서로 상태를 공유하기 위한 용도.
// (docs/05-frontend-design.md 3장: "상태 변경 시 관련 DOM만 다시 그리는" 방식)

const state = {
  searchQuery: "",
  selectedStationId: null,
  selectedLineId: null,
  sidebarOpen: false,
  legendOpen: false,
};

const listeners = new Set();

export function getState() {
  return state;
}

/** 상태 일부를 변경하고 모든 구독자에게 알린다. */
export function setState(patch) {
  Object.assign(state, patch);
  listeners.forEach((listener) => listener(state));
}

/** 상태 변경을 구독한다. 반환값을 호출하면 구독이 해제된다. */
export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
