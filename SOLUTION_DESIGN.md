# Solution Design: Brightline HR API Integration Health

## Problem

Brightline HR's Platform Ops team currently learns about broken third-party integrations from customer tickets, often days after failures begin. They need a morning-ready view that turns request logs into a clear status for each integration, explains the evidence behind that status, and helps them decide where to investigate first. Since "unhealthy" is not yet defined by agreed service objectives, the first version should make its judgments explicit and tunable rather than imply that one threshold fits every integration.

## Assumptions

- A log record represents one request and includes an integration, timestamp, outcome/status, and latency. Logs are complete enough for an initial assessment, and timestamps use a consistent time basis.
- The supplied synthetic data is representative enough to demonstrate the workflow, but not to establish production thresholds. The initial live assessment uses a rolling 60-minute window, refreshed every 60 seconds.
- Six or more requests in the window are required before rate-based rules make a verdict. Below that, rates are too unstable to treat as reliable; low traffic is assessed separately against the expected request cadence.
- A 5xx response or timeout is a service failure. 429s and 401/403s are tracked independently because they suggest different investigation paths. Client errors are shown in reports but are not treated as service failures in the live error-rate rule.
- Thresholds are starting defaults for the demonstration, not customer-validated SLOs. Each integration's cadence and acceptable error/latency profile may differ.
- This is a case-study prototype, not a deployment for an existing Brightline HR Ops team. The dashboard is designed for the stated operator persona; workflows, ownership, and escalation would need validation with stakeholders before production use.

## Proposed approach

Ingest request logs into a relational store, map requests to integrations, and classify outcomes into success, server error, timeout, throttling, authentication, or client error. For the demo, sample JSON seeds an empty database. Preserve request events as the shared basis for live evaluation and historical reports.

Evaluate every integration every minute over the most recent hour. Use explicit, ordered rules with tunable thresholds:

- **Failure rate:** 5xx plus timeout share is Amber at 15% and Red at 30%, with at least six requests. Consecutive failures add an independent signal (Amber at three, Red at five) so a short run is not hidden by earlier successes.
- **Throttling and authentication:** 429s are judged separately (5% Amber, 50% Red); any 401/403 rate above 1% is Amber, with 50% Red. This makes actionable credential or partner rate-limit issues visible even when 5xx/timeout rate is low.
- **Latency:** compare the median of the latest three successful requests with the median successful-request latency in the window. A 1.5x increase is Amber and 3x is Red. Successful calls are used so timeouts do not distort the latency comparison; too few successes means this rule abstains.
- **Silence:** compare time since the last request with the integration's expected cadence (defaulting to five minutes until configured). Silence becomes Amber after two expected cadences and Red after five.

Roll the rules up using the worst status, Green < Amber < Red, rather than an average that could hide a severe issue. Display each integration's status and contributing reasons, plus dashboard-wide counts of Green, Amber, and Red integrations. This gives Ops a fast portfolio view without allowing a healthy high-volume integration to mask a failing one. Apply hysteresis when a previously unhealthy integration recovers, requiring the measured rate to fall below a lower recovery threshold before stepping down.

Persist the latest evaluation as a snapshot so the live dashboard reads prepared statuses rather than recalculating on each page view. A scheduled run and stale-snapshot refresh keep the view reasonably current. Daily, weekly, and monthly reports reuse the same request data to show bucketed status, failure mix, latency percentiles, coverage gaps, and incident periods. Historical buckets use rate and coverage checks, not live-only signals such as current silence or consecutive failures.

The Spring Boot application presents the live view, integration drill-down, and reports; CSV export supports further analysis. An optional incident-summary feature can turn aggregate metrics into a short sentence. It does not affect health status, sends no raw request logs, and has a deterministic local fallback if the external API is disabled or unavailable.

## Trade-offs considered

- **Fixed thresholds vs. learned/anomaly-based detection:** explicit rules are explainable and practical with a small synthetic dataset. They are less adaptive and must be revisited against real traffic and service objectives.
- **Worst-rule status vs. weighted score:** worst-of is conservative but keeps a single severe signal visible. A weighted score can better express nuance but risks obscuring an urgent condition and is harder to explain.
- **One-hour window vs. shorter/longer windows:** one hour smooths isolated failures while staying relevant to a morning investigation. It can hide a brief outage or react slowly to a newly failing integration; reports preserve longer context. Window length should ultimately be chosen with Ops based on incident patterns.
- **Relative latency vs. absolute latency SLO:** comparing an integration to itself avoids assuming that all partners have the same baseline. A rolling in-window baseline can dilute a gradual slowdown, and without a contractual latency target it cannot establish whether an otherwise stable integration is too slow.
- **Snapshots and H2 vs. recalculation and production infrastructure:** snapshots make the dashboard inexpensive to serve; in-memory H2 keeps the demo easy to run. This version does not provide durable production storage, multi-instance coordination, or a defined retention policy.
- **Optional LLM summaries vs. deterministic-only text:** an LLM may help operators scan metrics, but adds cost, latency, external dependency, and data-governance considerations. It remains an optional presentation layer and is not part of the decision path.

## With more time, data, or budget

- **Validate the operational need:** interview the intended users and stakeholders (the case study does not establish that an Ops team currently exists). Agree what warrants attention, who owns each integration, expected cadence, service objectives, alert destinations, and escalation expectations.
- **Establish log access and ingestion:** find out how each provider or internal integration makes logs available, including whether there is an authenticated API, downloadable file, object-storage location, or scheduled export. Choose a per-source ingestion approach, then define a common event schema, timestamp/time-zone handling, field mapping, validation, deduplication, retry/back-pressure, and monitoring for ingestion gaps. Avoid assuming every source has a directly downloadable log path.
- **Tune health decisions with evidence:** review representative logs from normal periods and known incidents. Measure false positives, missed incidents, and time-to-detection; tune thresholds and latency baselines per integration; distinguish a genuinely silent integration from missing telemetry.
- **Add alerts:** provide configurable alert policies and destinations (for example, email, chat, or a webhook), with severity, deduplication, acknowledgement, recovery notifications, and ownership. Start with a clear boundary between dashboard status and notification policy so alert noise can be tuned without changing health calculations.
- **Integrate ticketing:** support creating or linking an incident directly from an integration's dashboard view, beginning with a ticketing platform such as ServiceNow. Map health evidence and relevant time-window metrics into a ticket, prevent duplicate tickets for the same active incident, and capture ticket status/reference back in the dashboard. Confirm authentication, permissions, field mapping, and customer workflow before selecting the exact integration design.
- **Plan integration onboarding in the UI:** add a placeholder or guided form for users to register an integration, its owner, expected cadence, and applicable thresholds. Validate the configuration and clearly show whether log ingestion is connected; creating a record alone must not imply that the integration is monitored.
- **Prepare production deployment:** replace in-memory H2 with managed durable storage, define retention and access controls, and package the application as a Docker image for deployment to a Kubernetes cluster. Add readiness/liveness probes, resource limits, secret management, observability, and migration/rollback practices. Introduce queued evaluation and multi-instance coordination only if measured scale requires them.
- **Consider a React UI migration:** retain the existing API and health/reporting boundaries while replacing Thymeleaf incrementally, if richer configuration workflows or frontend scaling justify the cost. Preserve accessible status explanations and avoid making a framework migration a prerequisite for the core monitoring workflow.
- **Expand analytics carefully:** if sufficient representative data becomes available, evaluate seasonality-aware baselines or anomaly detection alongside explainable rules. Any expanded AI use should follow privacy review and measured quality/cost evaluation; retain the deterministic path as a safe fallback.
