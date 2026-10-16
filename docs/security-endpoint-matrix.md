# API and public-route access matrix

The Spring Security filter chain in `SecurityConfig` is the authorization source of
truth. Every `/api/**` route must match an explicit public or role-based rule.
Unmatched API routes are denied; the SPA's non-API routes remain available to
the frontend so it can render public pages or redirect unauthenticated users to
login.

## Automated verification

`SecurityAuthorizationIntegrationTest` runs 37 individually reported
unauthenticated-request cases across the main API modules and asserts each
protected route returns JSON `401`. A separate guard test ensures the matrix
never falls below the required 20 cases. The test also verifies that invalid
bearer tokens are rejected and intentional public routes remain reachable.

These checks run as part of the backend Maven test suite. The
`Quality checks` GitHub Actions workflow runs backend tests and frontend lint,
tests, and build on pull requests targeting `develop` and pushes to `develop`.

## Public API routes

These API routes are intentionally reachable without an access token. A supplied
invalid bearer token is still rejected by the JWT resource-server filter.

| Method | Route | Purpose |
| --- | --- | --- |
| POST | `/api/auth/login` | Sign in |
| POST | `/api/auth/refresh` | Refresh access token using refresh cookie |
| POST | `/api/auth/logout` | Clear refresh cookie |
| POST | `/api/auth/forgot-password` | Start password reset |
| POST | `/api/auth/reset-password` | Complete password reset |
| GET | `/api/public/room-types` | Public room-type list |
| GET | `/api/public/room-types/{roomTypeId}` | Public room-type detail |
| GET | `/api/public/room-types-catalog` | Public room-type catalog |
| GET | `/api/public/room-type-cards` | Public room-type cards |
| GET | `/api/public/rooms/search` | Search available rooms |
| GET | `/api/public/quote` | Public booking quote |
| POST | `/api/public/bookings` | Create public booking |
| POST | `/api/public/bookings/lookup` | Look up booking with booking code and email |
| GET | `/uploads/**` | Public room-type image files |

`POST /api/public/bookings/walk-in` is not public; it requires `RECEPTIONIST`.

## Authenticated API routes

`{id}`, `{roomId}`, `{bookingCode}`, and similar segments represent one path
segment. `**` is used only where nested paths are intentionally covered.

| Method and route(s) | Required role |
| --- | --- |
| `GET /api/internal/me`, `POST /api/account/change-password` | Any authenticated active user |
| `GET /api/admin/audit-logs` | `ADMIN` |
| `GET /api/admin/users` | `ADMIN`, `OWNER` |
| `GET /api/admin/security/endpoint-permissions`, `GET /api/admin/security/endpoint-discrepancies`, `POST /api/admin/security/endpoint-discrepancies` | `ADMIN` |
| `POST /api/admin/sample-data/reload` | `OWNER` |
| `POST /api/admin/users`, `PATCH /api/admin/users/{id}/status`, `PUT /api/admin/users/{id}`, `POST /api/admin/users/{id}/resend-temporary-password` | `ADMIN` |
| `POST /api/admin/reminders/run`, `GET /api/admin/email-logs` | `ADMIN` |
| `GET /api/reports/occupancy`, `/api/reports/occupancy.csv`, `/api/reports/occupancy/detailed` | `OWNER` |
| `GET /api/maintenance-requests/cleaning-room-ids` | `HOUSEKEEPING` |
| `GET /api/maintenance-requests`, `GET /api/maintenance-requests/{id}` | `OWNER`, `RECEPTIONIST`, `HOUSEKEEPING` |
| `POST /api/maintenance-requests/{id}/accept`, `/start`, `/repair-completed`, `/verify`, `PUT /api/maintenance-requests/{id}/vendor` | `OWNER` |
| `POST /api/maintenance-requests/{id}/cleaning-completed` | `HOUSEKEEPING` |
| `GET /api/notifications`, `/stream`, `/unread-count`, `PATCH /api/notifications/{id}/read` | `OWNER`, `RECEPTIONIST` |
| `GET /api/rooms`, `GET /api/rooms/{roomId}/history` | `ADMIN`, `OWNER`, `RECEPTIONIST`, `HOUSEKEEPING` |
| `GET /api/rooms/calendar`, `GET /api/rooms/{roomId}/checkout-preview` | `RECEPTIONIST` |
| `PATCH /api/rooms/{roomId}/status` | `RECEPTIONIST`, `HOUSEKEEPING` |
| `PATCH /api/rooms/{roomId}/maintenance` | `RECEPTIONIST` |
| `PATCH /api/rooms/{roomId}/incident` | `HOUSEKEEPING` |
| `POST /api/rooms/{roomId}/check-in`, `POST /api/rooms/{roomId}/check-out` | `RECEPTIONIST` |
| `POST /api/room-management/rooms`, `GET /api/room-management/rooms`, `PATCH /api/room-management/rooms/{roomId}`, `GET /api/room-management/rooms/{roomId}/note` | `ADMIN`, `OWNER` |
| `GET /api/room-types`, `GET /api/room-types/{id}/images` | `ADMIN`, `OWNER`, `RECEPTIONIST`, `HOUSEKEEPING` |
| `POST /api/room-types`, `PUT /api/room-types/{id}`, `PATCH /api/room-types/{id}/status`, `DELETE /api/room-types/{id}`, `POST /api/room-types/{id}/amenities/{amenityId}`, `DELETE /api/room-types/{id}/amenities/{amenityId}`, `POST /api/room-types/{id}/images`, `PUT /api/room-types/{id}/images/reorder`, `DELETE /api/room-types/{id}/images/{imageId}` | `ADMIN`, `OWNER` |
| `GET /api/amenities` | `ADMIN`, `OWNER`, `RECEPTIONIST`, `HOUSEKEEPING` |
| `POST /api/amenities`, `PUT /api/amenities/{id}`, `PATCH /api/amenities/{id}/status`, `DELETE /api/amenities/{id}` | `ADMIN`, `OWNER` |
| `GET /api/settings` | `OWNER`, `ADMIN`, `RECEPTIONIST` |
| `GET /api/settings/history` | `OWNER`, `ADMIN` |
| `PUT /api/settings` | `OWNER` |
| `GET /api/price-overrides` | `OWNER`, `ADMIN`, `RECEPTIONIST` |
| `POST /api/price-overrides`, `POST /api/price-overrides/preview`, `PUT /api/price-overrides/{id}`, `DELETE /api/price-overrides/{id}` | `OWNER` |
| `GET /api/pricing/quote` | `OWNER`, `ADMIN`, `RECEPTIONIST` |
| `GET /api/bookings`, `GET /api/bookings/latest`, `GET /api/bookings/check-in-options`, `GET /api/bookings/{id}/summary`, `GET /api/bookings/{id}/available-rooms`, `GET /api/bookings/{id}/room-change-history`, `GET /api/bookings/{id}/history`, `GET /api/bookings/{id}/details`, `GET /api/bookings/{id}/cancellation-info`, `GET /api/bookings/{bookingCode}/cancellation-preview` | `ADMIN`, `OWNER`, `RECEPTIONIST` |
| `POST /api/bookings`, `POST /api/bookings/{id}/preview`, `POST /api/bookings/{bookingCode}/cancel`, `PUT /api/bookings/{id}/confirm`, `PUT /api/bookings/{id}/room`, `PUT /api/bookings/{id}/room-change`, `PUT /api/bookings/{id}/cancel`, `PUT /api/bookings/{id}` | `RECEPTIONIST` |
| `POST /api/bookings/{id}/deposit-adjustments`, `POST /api/bookings/{id}/surcharges` | `OWNER`, `RECEPTIONIST` |
| `PUT /api/bookings/{id}/deposit`, `PUT /api/bookings/{id}/deposits/**`, `DELETE /api/bookings/{id}/deposit`, `DELETE /api/bookings/{id}/deposits/**`, `DELETE /api/bookings/{id}` | `OWNER`, `RECEPTIONIST` |
| `POST /api/public/bookings/walk-in` | `RECEPTIONIST` |

Any `/api/**` route not covered above is denied by default. Authenticated requests
that lack the required role receive `403`; missing or invalid tokens receive
`401`. Both outcomes are logged with the HTTP method and route path; credentials,
request bodies, query strings, and token values are not logged.

## Permission review screen

The admin-only screen at `/security-endpoint-matrix` compares this approved
matrix with the effective permissions of registered API handler methods. The
actual side is read from the same ordered policy catalog used by
`SecurityConfig`, so the report reflects the rules that protect requests rather
than a second backend copy of those rules. The API report is available at
`GET /api/admin/security/endpoint-permissions`.

The screen highlights and filters mismatches but does not modify authorization.
When a route is added or its approved access changes, update the approved
frontend matrix and this document independently, then review any reported
differences.

For a mismatch that cannot be fixed in the sprint, an administrator can create
an endpoint discrepancy ticket and assign it to an active staff account. The
ticket retains the approved and effective permission snapshots and its
assignee. The matrix marks an outstanding mismatch as `Đã ghi nhận` when a
ticket exists, and displays `Đã khắc phục` once the live permissions match the
approved matrix again.

## Public frontend pages

`AppRoutes` intentionally renders these paths without a signed-in user:

- `/trang-chu`
- `/forgot-password`, `/reset-password`
- `/tim-phong`
- `/dat-phong`
- `/tra-cuu-booking`, `/tra-cuu-dat-phong`
- `/danh-sach-loai-phong`
- `/loai-phong/{roomTypeId}`
- Legacy redirects `/`, `/loai-phong`, and `/phong/{roomTypeId}`

The login screen is rendered for other frontend paths when no user is signed in.
Client-side route rendering is not an API security boundary; all protected data
and actions must remain guarded by the backend matrix above.

## Verification

`SecurityAuthorizationIntegrationTest` checks protected API families with no
token, invalid bearer tokens on public APIs, public API access without a token,
and existing role-specific authorization behavior.
