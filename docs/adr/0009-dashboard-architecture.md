# 0009. Dashboard architecture

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

The dashboard shows what analysis-service knows: repositories, their tests, why a test is flaky, and the trend of
failures. It also lets the team quarantine and release tests. It reads analysis-service's query API
([ADR-0007](0007-flakiness-detection-and-test-status.md)) and has no data of its own.

How is the dashboard built and served, and how does it reach the API?

## Considered options

Application type:

1. A single page application (Vite, React), served as static files
2. A server-rendered framework (Next.js, React Router framework mode)

Reaching the API:

- A. The browser calls analysis-service directly, which allows the dashboard's origin (CORS)
- B. A backend for frontend (BFF) service that aggregates the services' APIs
- C. The web server that serves the dashboard proxies `/api/` to analysis-service (same origin)

## Decision outcome

Chosen options: **1, a single page application**, and **C, a same-origin proxy.**

- **Why not server rendering:** the dashboard is an internal tool behind a login in any real deployment, so search
  engines and first-paint time do not matter. A server-rendered framework would add a Node.js server to run and
  secure, for no benefit to its users.
- **Why a proxy:** the browser sees one origin, so analysis-service needs no CORS configuration and its port does not
  have to be reachable from the browser. nginx, which serves the static files anyway, does the proxying.
- **Why not a BFF:** the dashboard needs one service's API, in the shape that API already has. A BFF becomes worth it
  once a page combines several services; until then it would be a service that only forwards requests.

### Inside the application

| Concern | Choice | Reason |
|---|---|---|
| Server state | TanStack Query | Caching, refetching and invalidation of API data; the dashboard has almost no client state |
| Client state | the URL | Filters and time ranges are search parameters, so every view is a link that can be shared |
| Routing | React Router (library mode) | Three pages; no data loaders or server needed |
| API types | written by hand in `src/api/types.ts` | Five small DTOs; generating them from OpenAPI would add a build step and a springdoc dependency for little gain |
| Errors | `ApiError` with the RFC 9457 problem details | One error shape for 4xx, 5xx, proxy errors and network failures |
| Styling | CSS modules and design tokens (light and dark) | No styling library to learn or upgrade; status colors always come with a text label |

Modules follow the hexagonal idea of the services loosely: `api/` is the only code that knows HTTP; the
`features/` pages use its hooks and know nothing about URLs or `fetch`.

### Serving

- A multi-stage image builds the bundle with Node.js and serves it from unprivileged nginx (non-root, port 8080).
- Asset names contain a content hash and are cached forever; `index.html` is always revalidated, so a new release is
  picked up on the next page load.
- A Content Security Policy restricts scripts, styles and requests to the page's own origin. Inline styles are
  allowed, because the chart library sets style attributes.
- nginx resolves the analysis-service host through Docker's DNS at request time, so a restarted service with a new
  IP address is found again.

### Consequences

- Good: two static concerns (files and a proxy) instead of another application server.
- Good: the services stay reachable only through their APIs; the browser never needs their addresses.
- Bad: the hand-written types can drift from the API. The end-to-end tests ([ADR-0010](0010-frontend-testing-strategy.md))
  catch a drift that breaks a page; a generated client is the next step if the API grows.
- Bad: quarantine and release have no authentication, like every API in the MVP. A deployment needs a login in front
  of the dashboard and the services.
- Bad: statuses are refreshed when a page is opened, refocused or 15 seconds after the last fetch; there are no live
  updates. A push channel (server-sent events) would be needed for that.
