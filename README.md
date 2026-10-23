# Integration Health Monitor

Spring Boot 3 + Thymeleaf + MySQL dashboard that triages integrations into Green / Amber / Red
from raw request logs. A scheduler evaluates every 60 s and writes one snapshot row per
integration; pages only read snapshots.

## Run
1. Java 17+, Maven, h2 db (database `integration_monitor` is created automatically).
2. `mvn spring-boot:run`, then open http://localhost:8080

On first start the rolling 30-day sample in `src/main/resources/sample/integration_logs_month.json` is loaded
(only if `request_log` is empty). Set `health.sample-logs:` to blank to disable seeding.

## Test
`mvn test`. `SampleDataAcceptanceTest` asserts Slack/Workday GREEN, DocuSign AMBER, ADP/Greenhouse RED,
plus silent and auth-failing integrations RED.

## Endpoints
`/` dashboard, `/dashboard/fragment` auto-refreshed lanes, `/integrations/{id}` drill-down, `/api/health` JSON.
The dashboard's **Save as PDF** button opens the browser print dialog for the currently selected view;
choose **Save as PDF** there to preserve the dashboard's UI layout. `/report/download?period=...`
downloads the selected report as CSV.

## Tuning (application.yml, under `health`)
- `reference-time`: `latest-log` for the rolling sample data, **`system` for real use**.
- Window, minimum request volume, hysteresis (`recovery-factor`) and per-rule thresholds.

## Live incident summaries
The Live dashboard adds a one-sentence summary for each integration using aggregate request counts from the configured health window. To enable LLM summaries, set `GEMINI_API_KEY` before starting the application. The dashboard uses Gemini `gemini-3.8-flash` by default; endpoint and model can be changed under `incident-summary` in `application.yml`. Only aggregate metrics are sent, requests have bounded timeouts, and unchanged inputs are cached for five minutes. If the key is missing or the API request fails, a deterministic metrics-based summary is shown instead. Historical reports and integration detail pages are unchanged.

## Rules (`rules/`, worst-of rollup; add a rule with a `@Component @Order(n)` class)
| Rule | Looks at |
|---|---|
| ErrorRateRule | 5xx + timeouts / total (min-volume guard, hysteresis) |
| ConsecutiveFailureRule | trailing 5xx/timeout run |
| ThrottleRule | 429 share (recoverable, so rarely Red) |
| AuthRule | 401/403 (expired credentials) |
| LatencyRule | recent vs baseline median, successful calls only |
| SilenceRule | minutes since last request vs expected cadence |

## Layout
`domain`, `repository`, `config` (properties + clock), `ingest` (JSON loader), `rules`,
`service` (stats + evaluation), `scheduler`, `controller`, templates in `resources/templates`.

## Views (dropdown in the header)
Live (last hour, rule-based Green/Amber/Red lanes) and Daily / Weekly / Monthly reports. Reports show per-integration
cards: a heat strip (hourly cells for 24 h, 24-hour cells for 7 and 30 days), success %, 5xx+timeout %, 429 %, auth errors,
p50/p95 latency and data coverage. Cells and the period total are judged with the same error-rate / 429 / auth thresholds
as the live view, plus a coverage check (under 80% of expected requests = Amber, "data gap"). Reports are cached for 30 s.

## Sample data
`python3 tools/generate_logs.py` regenerates a rolling 30-day sample ending today (deterministic status patterns). Story: Slack has
a short outage 13 days after the start; ADP degrades from day 10; DocuSign hits 429s in business hours from day 10; Workday has
an expired-token window on day 5 and a 5 h silence on day 22; Greenhouse timeouts worsen from day 15. The final hour follows the
supplied sample, so the Live view keeps its original Green/Amber/Red picture.
