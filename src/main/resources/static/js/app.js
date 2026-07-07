const form = document.querySelector("#searchForm");
const coverage = document.querySelector("#coverage");
const stepCount = document.querySelector("#stepCount");
const radius = document.querySelector("#radius");
const routeSummary = document.querySelector("#routeSummary");
const planningTime = document.querySelector("#planningTime");
const listingFetchTime = document.querySelector("#listingFetchTime");
const totalElapsedTime = document.querySelector("#totalElapsedTime");
const listingProvider = document.querySelector("#listingProvider");
const progressPercent = document.querySelector("#progressPercent");
const progressBar = document.querySelector("#progressBar");
const progressText = document.querySelector("#progressText");
const steps = document.querySelector("#steps");
const listings = document.querySelector("#listings");
const listingCount = document.querySelector("#listingCount");
const sortOrder = document.querySelector("#sortOrder");

let currentListings = [];
let progressTimer;

async function search(event) {
  event?.preventDefault();

  const params = new URLSearchParams(new FormData(form));
  startProgress();

  try {
    const response = await fetch(`/api/search/nationwide?${params.toString()}`);
    const body = await response.json();

    finishProgress();
    renderResult(body.data);
  } catch (error) {
    failProgress();
    listings.innerHTML = '<div class="empty">데이터를 불러오지 못했습니다.</div>';
  }
}

function renderResult(data) {
  coverage.textContent = `${data.coveragePercent}%`;
  stepCount.textContent = data.searchRegionCount;
  radius.textContent = `${data.optimizedRadiusKm}km`;
  routeSummary.textContent = `${data.searchRegionCount}개 거점으로 전국을 순회합니다. 예상 이동거리 ${data.totalMoveKm}km`;
  planningTime.textContent = `${data.planningTimeMs}ms`;
  listingFetchTime.textContent = `${data.listingFetchTimeMs}ms`;
  totalElapsedTime.textContent = `${data.totalElapsedTimeMs}ms`;
  listingProvider.textContent = data.listingProvider;
  currentListings = data.listings;
  listingCount.textContent = `${currentListings.length}건`;

  steps.innerHTML = "";
  data.steps.forEach((step) => {
    const item = document.createElement("li");
    item.className = "step-card";
    item.innerHTML = `
      <div>
        <strong>${escapeHtml(step.region.name)}</strong>
        <span>${step.distanceFromPreviousKm}km 이동</span>
      </div>
      <small>신규 ${step.newlyCoveredRegions}곳 / 누적 ${step.coveragePercent}%</small>
    `;
    steps.appendChild(item);
  });

  renderListings();
}

function renderListings() {
  const sortedListings = sortListings(currentListings, sortOrder.value);

  listings.innerHTML = "";
  if (sortedListings.length === 0) {
    listings.innerHTML = '<div class="empty">검색 결과가 없습니다.</div>';
    return;
  }

  sortedListings.forEach((listing) => {
    const item = document.createElement("article");
    item.className = "listing-card";
    const badges = listing.directBuy ? '<span class="listing-badge">바로구매</span>' : "";
    item.innerHTML = `
      <div>
        <strong>${escapeHtml(listing.title)}${badges}</strong>
        <span>${escapeHtml(listing.regionName)} · ${escapeHtml(listing.postedAt)}</span>
        <small>${escapeHtml(listing.searchedFrom)}</small>
      </div>
      <p>${escapeHtml(listing.price)}</p>
    `;
    listings.appendChild(item);
  });
}

function sortListings(source, order) {
  const listingsToSort = [...source];
  if (order === "priceDesc") {
    return listingsToSort.sort((a, b) => priceForSort(b) - priceForSort(a));
  }
  if (order === "priceAsc") {
    return listingsToSort.sort((a, b) => priceForSort(a) - priceForSort(b));
  }
  return listingsToSort;
}

function priceForSort(listing) {
  return Number.isFinite(listing.priceValue) ? listing.priceValue : -1;
}

function startProgress() {
  clearInterval(progressTimer);
  setProgress(0, "검색 준비 중");
  form.querySelector("button").disabled = true;
  const startedAt = Date.now();

  progressTimer = setInterval(() => {
    const elapsed = Date.now() - startedAt;
    const percent = Math.min(94, Math.floor(100 * (1 - Math.exp(-elapsed / 18000))));
    setProgress(percent, `${percent}% 실행 중`);
  }, 300);
}

function finishProgress() {
  clearInterval(progressTimer);
  setProgress(100, "검색 완료");
  form.querySelector("button").disabled = false;
}

function failProgress() {
  clearInterval(progressTimer);
  setProgress(0, "검색 실패");
  form.querySelector("button").disabled = false;
}

function setProgress(percent, text) {
  progressPercent.textContent = `${percent}%`;
  progressBar.style.width = `${percent}%`;
  progressText.textContent = text;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

form.addEventListener("submit", search);
sortOrder.addEventListener("change", renderListings);

listings.innerHTML = '<div class="empty">검색어를 입력하고 검색을 시작하세요.</div>';
