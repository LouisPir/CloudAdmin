import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

// ── Custom metrics ──────────────────────────────────────────────
const listLatency       = new Trend("latency_list_shows");
const searchLatency     = new Trend("latency_search");
const filterLatency     = new Trend("latency_filter");
const topDirectors      = new Trend("latency_top_directors");
const topGenres         = new Trend("latency_top_genres");
const statsCategories   = new Trend("latency_stats_categories");
const statsYearly       = new Trend("latency_stats_yearly");
const singleShow        = new Trend("latency_single_show");
const errorRate         = new Rate("errors");

// ── Base URL ────────────────────────────────────────────────────
const BASE = "http://127.0.0.1:8000/netflix";

// ── Load stages ─────────────────────────────────────────────────
// Ramp from 1 → 50 → 100 users over ~3 minutes
export const options = {
  stages: [
    { duration: "30s", target: 10 },   // warm-up
    { duration: "30s", target: 30 },   // ramp up
    { duration: "1m",  target: 50 },   // sustained medium load
    { duration: "30s", target: 100 },  // peak load
    { duration: "30s", target: 0 },    // cool down
  ],
  thresholds: {
    http_req_duration: ["p(95)<2000"],  // 95% of requests under 2s
    errors:           ["rate<0.1"],     // error rate below 10%
  },
};

// ── Helper ──────────────────────────────────────────────────────
function makeRequest(url, metricTrend) {
  const res = http.get(url);
  metricTrend.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, {
    "status is 200": (r) => r.status === 200,
    "has body":      (r) => r.body.length > 0,
  });
  return res;
}

// ── Main scenario ───────────────────────────────────────────────
export default function () {
  // 1. Paginated list (simulates browsing)
  const page = Math.floor(Math.random() * 5) + 1;
  makeRequest(`${BASE}/shows?page=${page}&per_page=20`, listLatency);

  // 2. Single show lookup (random ID 1–500)
  const id = Math.floor(Math.random() * 500) + 1;
  makeRequest(`${BASE}/shows/${id}`, singleShow);

  // 3. Title search (common terms)
  const searchTerms = ["love", "world", "man", "dark", "night", "king", "life"];
  const term = searchTerms[Math.floor(Math.random() * searchTerms.length)];
  makeRequest(`${BASE}/shows/search?q=${term}`, searchLatency);

  // 4. Filter (mixed combinations)
  const filters = [
    "category=Movie&country=United%20States",
    "category=TV%20Show&rating=TV-MA",
    "release_year=2020",
    "genre=Comedy&category=Movie",
    "country=India&rating=TV-14",
  ];
  const filter = filters[Math.floor(Math.random() * filters.length)];
  makeRequest(`${BASE}/shows/filter?${filter}`, filterLatency);

  // 5. Aggregation queries
  makeRequest(`${BASE}/shows/top-directors?n=10`, topDirectors);
  makeRequest(`${BASE}/shows/top-genres?n=10`, topGenres);
  makeRequest(`${BASE}/shows/stats/categories`, statsCategories);
  makeRequest(`${BASE}/shows/stats/yearly`, statsYearly);

  sleep(0.5); // small pause between iterations
}

// ── Summary ─────────────────────────────────────────────────────
export function handleSummary(data) {
  // Print a quick console summary of key metrics
  const lines = [
    "\n╔══════════════════════════════════════════════════╗",
    "║         NETFLIX API — BASELINE LOAD TEST         ║",
    "╠══════════════════════════════════════════════════╣",
  ];

  const metrics = [
    ["List shows",      "latency_list_shows"],
    ["Single show",     "latency_single_show"],
    ["Search",          "latency_search"],
    ["Filter",          "latency_filter"],
    ["Top directors",   "latency_top_directors"],
    ["Top genres",      "latency_top_genres"],
    ["Stats categories","latency_stats_categories"],
    ["Stats yearly",    "latency_stats_yearly"],
  ];

  for (const [label, key] of metrics) {
    const m = data.metrics[key];
    if (m && m.values) {
      const p50 = m.values["p(50)"]?.toFixed(1) ?? "N/A";
      const p95 = m.values["p(95)"]?.toFixed(1) ?? "N/A";
      const p99 = m.values["p(99)"]?.toFixed(1) ?? "N/A";
      lines.push(`║ ${label.padEnd(18)} p50: ${p50.padStart(7)}ms  p95: ${p95.padStart(7)}ms  p99: ${p99.padStart(7)}ms ║`);
    }
  }

  const reqs = data.metrics["http_reqs"];
  const errs = data.metrics["errors"];
  lines.push("╠══════════════════════════════════════════════════╣");
  lines.push(`║ Total requests:  ${reqs?.values?.count ?? "N/A"}`.padEnd(51) + "║");
  lines.push(`║ Throughput:      ${reqs?.values?.rate?.toFixed(1) ?? "N/A"} req/s`.padEnd(51) + "║");
  lines.push(`║ Error rate:      ${((errs?.values?.rate ?? 0) * 100).toFixed(2)}%`.padEnd(51) + "║");
  lines.push("╚══════════════════════════════════════════════════╝\n");

  console.log(lines.join("\n"));

  return {
    "baseline_results.json": JSON.stringify(data, null, 2),
  };
}
