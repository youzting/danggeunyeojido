const form = document.querySelector("#searchForm");
const coverage = document.querySelector("#coverage");
const stepCount = document.querySelector("#stepCount");
const radius = document.querySelector("#radius");
const routeSummary = document.querySelector("#routeSummary");
const steps = document.querySelector("#steps");
const listings = document.querySelector("#listings");
const listingCount = document.querySelector("#listingCount");

async function search(event) {
  event?.preventDefault();

  const params = new URLSearchParams(new FormData(form));
  const response = await fetch(`/api/search/nationwide?${params.toString()}`);
  const body = await response.json();

  renderResult(body.data);
}

function renderResult(data) {
  coverage.textContent = `${data.coveragePercent}%`;
  stepCount.textContent = data.searchRegionCount;
  radius.textContent = `${data.optimizedRadiusKm}km`;
  routeSummary.textContent = `${data.searchRegionCount}개 거점으로 전국을 순회합니다. 예상 이동거리 ${data.totalMoveKm}km`;
  listingCount.textContent = `${data.listings.length}건`;

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

  listings.innerHTML = "";
  if (data.listings.length === 0) {
    listings.innerHTML = '<div class="empty">검색 결과가 없습니다.</div>';
    return;
  }

  data.listings.forEach((listing) => {
    const item = document.createElement("article");
    item.className = "listing-card";
    item.innerHTML = `
      <div>
        <strong>${escapeHtml(listing.title)}</strong>
        <span>${escapeHtml(listing.regionName)} · ${escapeHtml(listing.postedAt)}</span>
      </div>
      <p>${escapeHtml(listing.price)}</p>
    `;
    listings.appendChild(item);
  });
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

search().catch(() => {
  listings.innerHTML = '<div class="empty">데이터를 불러오지 못했습니다.</div>';
});
