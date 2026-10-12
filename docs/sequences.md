# Ari sequences

Last updated 2026-10-08. The `ari` repo keeps its own copy with only the client side; this one also
shows the server steps. The sign-in rules and error codes are in [architecture.md](architecture.md);
the tables are in [database.md](database.md).

1. [Register or sign in (F1)](#1-register-or-sign-in-f1)
2. [Token refresh (F1)](#2-token-refresh-f1)
3. [Server-rendered library page (F2)](#3-server-rendered-library-page-f2)
4. [Save and delete a highlight, free limit (F3, F4)](#4-save-and-delete-a-highlight-free-limit-f3-f4)
5. [PDF or Word import (F11)](#5-pdf-or-word-import-f11)
6. [Save from the extension (F13)](#6-save-from-the-extension-f13)
7. [Paddle checkout and webhook (F15)](#7-paddle-checkout-and-webhook-f15)

## 1. Register or sign in (F1)

```mermaid
sequenceDiagram
  participant B as Browser
  participant A as ari-api
  alt Register
    B->>A: POST /api/v1/auth/register (username, password, fullName, birthDate)
    A->>A: Normalize username, hash password, save user (a taken username is 409)
  else Sign in
    B->>A: POST /api/v1/auth/login (username, password)
    A->>A: Find user by username, check password_hash
  end
  A->>A: Create JWT (15 min) and refresh token (30 days, store only its hash)
  A-->>B: Me + Set-Cookie ari_access, ari_refresh (httpOnly)
  B->>A: GET /api/v1/highlights (browser sends the cookies)
  A-->>B: 200 OK + data
```

## 2. Token refresh (F1)

```mermaid
sequenceDiagram
  participant B as Browser
  participant W as ari server
  participant A as ari-api
  alt In the browser (lib/api.ts)
    B->>A: GET /api/v1/highlights (ari_access has expired)
    A-->>B: 401 UNAUTHORIZED
    B->>A: POST /api/v1/auth/refresh (ari_refresh cookie)
    A->>A: Find token by hash, check it is not revoked or expired
    A->>A: Revoke it, create a new pair with the same family_id
    A-->>B: 204 + new cookies
    B->>A: Retry GET /api/v1/highlights
    A-->>B: 200 OK + data
  else While rendering a page (middleware)
    B->>W: GET /library (ari_refresh only, the ari_access cookie is gone)
    W->>A: POST /api/v1/auth/refresh (forwards the Cookie header)
    A->>A: Check, revoke and rotate as above
    A-->>W: 204 + Set-Cookie ari_access, ari_refresh
    W->>W: Keep the new cookies in event.locals for this render
    W-->>B: HTML rendered with the new token + the same Set-Cookie headers
  end
  Note over B,A: Refresh fails: 401, both cookies are cleared, and the user goes to /login.<br/>A replaced token used again within 10 s gets a new pair while its session is alive. Later, it revokes the whole session.
```

- In the browser, several 401s at once share one refresh request.
- The `ari` server refreshes only when `ari_access` is missing. The cookie expires a minute before its
  JWT, so a cookie the browser still sends is always valid.

## 3. Server-rendered library page (F2)

```mermaid
sequenceDiagram
  participant B as Browser
  participant W as ari server
  participant A as ari-api
  B->>W: GET /library/{folderId}?q=spring (cookies)
  W->>W: Middleware: ari_access present, or refresh first (section 2)
  Note over B,W: No cookies at all: 302 to /login
  W->>W: Route runs createAsync with deferStream, lib/api.ts forwards the Cookie header
  par Folders
    W->>A: GET /api/v1/folders
    A-->>W: 200 FolderResponse list
  and Highlights
    W->>A: GET /api/v1/highlights?folderId={folderId}&q=spring
    A->>A: User id from the JWT, filter by user, folder and q, 50 per page
    A-->>W: 200 PageResponse (items, nextCursor)
  end
  W-->>B: Streamed HTML with the data in it
  B->>B: Hydrate
  B->>A: GET /api/v1/highlights?q=spring boot (typing, credentials include)
  A-->>B: 200 PageResponse
  B->>A: GET /api/v1/highlights?q=spring boot&cursor=... (Load more)
  A-->>B: 200 next page
```

- `ari-api` always filters by the signed-in user. A folder id that belongs to someone else returns
  404 `NOT_FOUND`.
- `nextCursor` is opaque: the last item's `(created_at, id)`, base64url-encoded. The next page holds
  the items older than it (keyset paging, no `OFFSET`). It is null on the last page.
- Typing in the search box waits about 300 ms, then updates `?q=` in the URL, so a reload renders the
  same results on the server.
- Phase 1 (F2) matches `q` case-insensitively against text and title in memory. F10 swaps in
  Postgres full-text search behind the same endpoint.

## 4. Save and delete a highlight, free limit (F3, F4)

```mermaid
sequenceDiagram
  participant B as Browser
  participant A as ari-api
  participant D as Database
  B->>A: POST /api/v1/highlights (kind, text, source fields, folderId)
  A->>A: Validate the body
  alt Invalid
    A-->>B: 400 VALIDATION_FAILED with field errors
  else Valid
    A->>D: BEGIN
    A->>D: UPDATE users SET saves_count + 1 if plan is PRO or saves_count is under 500
    alt 0 rows updated
      A->>D: ROLLBACK
      A-->>B: 403 LIMIT_REACHED
      B->>B: Show the limit banner with an upgrade link
    else 1 row updated
      A->>D: INSERT INTO highlights, then COMMIT
      A-->>B: 201 HighlightResponse (with id and createdAt)
    end
  end
  B->>A: DELETE /api/v1/highlights/{id}
  A->>D: BEGIN, DELETE FROM highlights WHERE id and user_id match
  alt 0 rows deleted
    A->>D: ROLLBACK
    A-->>B: 404 NOT_FOUND
  else 1 row deleted
    A->>D: UPDATE users SET saves_count - 1, then COMMIT
    A-->>B: 204
  end
```

- The limit check reads `users.plan` and `users.saves_count`, never the JWT `plan` claim, which can
  be up to 15 minutes old after an upgrade.
- The counter changes in the same transaction as the insert or delete, so it cannot drift.
- Free means at most 500 highlights stored at once: deleting gives the save back. Pro has no limit.
  Folders don't count.
- `GET /me` returns `plan`, `savesCount` and `savesLimit`; `/settings` shows them, and the banner shows
  once `savesCount` reaches `savesLimit`.
- Phase 1 (no database until F8): the same check and change run in memory under a per-user lock.

## 5. PDF or Word import (F11)

```mermaid
sequenceDiagram
  participant B as Browser
  participant A as ari-api
  participant S as File storage
  participant D as PostgreSQL
  participant P as Outbox publisher
  participant Q as RabbitMQ
  participant C as Import consumer
  B->>A: POST /api/v1/imports (multipart file)
  A->>A: Check the type (PDF or DOCX) and size, else 400 VALIDATION_FAILED
  A->>S: PUT imports/{userId}/{importId}
  A->>D: One transaction: INSERT imports (QUEUED) and INSERT outbox_events (import.process)
  A-->>B: 202 ImportJobResponse (QUEUED)
  loop Every second
    P->>D: SELECT unsent rows FOR UPDATE SKIP LOCKED
    P->>Q: Publish import.process (event id = outbox id), wait for the confirm
    P->>D: UPDATE outbox_events SET sent_at
  end
  Q->>C: Deliver import.process
  C->>D: Is the event id in processed_events?
  Note over C,D: Yes: ack and stop (a duplicate delivery)
  C->>D: UPDATE imports SET status PROCESSING
  C->>S: GET the file
  C->>C: PDFBox reads PDF highlight annotations with page numbers, POI reads DOCX highlights and comments
  alt Parsed, n highlights fit under the limit
    C->>D: One transaction: saves_count + n (check as in section 4), INSERT n highlights (kind FILE), status DONE, INSERT processed_events
    C->>S: DELETE the file
    C->>Q: ack
  else Parsed, n highlights would pass the free limit
    C->>D: One transaction: status FAILED, error_code LIMIT_REACHED, INSERT processed_events
    C->>S: DELETE the file
    C->>Q: ack
  else Error
    C->>Q: Reject, retry after 2 s, then 10 s, then 60 s
    Note over C,Q: Last attempt fails: status FAILED with error_code,<br/>file deleted, message goes to the dead-letter queue
  end
  loop Every 2 seconds until DONE or FAILED
    B->>A: GET /api/v1/imports/{id}
    A-->>B: 200 ImportJobResponse (status, highlightsCount)
  end
```

- The outbox row is written in the same transaction as the import row, so a job is never lost. The
  publisher can send a message twice (a crash after publishing, before `sent_at`); the
  `processed_events` check makes that harmless.
- If the first transaction fails, delete the stored file again.
- An import is all or nothing: if its highlights don't fit under the free limit, none are saved.
- Queues: `import.process`, plus one retry queue per delay (message TTL with a dead-letter exchange
  back to `import.process`) and `import.process.dlq`.
- F5 (mock, phase 1): `POST /imports` ignores the file and returns a `DONE` job with 0 highlights. No
  storage, outbox or queue.
- Photos are not imported here: OCR runs in the browser (F12) and saves through `POST /highlights`.

## 6. Save from the extension (F13)

```mermaid
sequenceDiagram
  participant X as Extension
  participant T as Website tab
  participant A as ari-api
  participant R as Redis
  Note over X,R: Connect once
  X->>T: Open /extension/connect on the website
  T->>A: POST /api/v1/auth/extension/code (cookies, signed in)
  A->>R: Store the code with the user id, 60 s, single use
  A-->>T: 200 with the code
  T->>X: The content script on /extension/connect passes the code
  X->>A: POST /api/v1/auth/extension/token (code)
  A->>R: GETDEL the code
  alt Missing or expired
    A-->>X: 401 UNAUTHORIZED
  else Found
    A->>A: Create JWT and refresh token (client EXTENSION)
    A-->>X: 200 accessToken, refreshToken, expiresIn
    X->>X: Keep the tokens in browser.storage.local
  end
  Note over X,R: Save a highlight
  X->>A: POST /api/v1/highlights (Bearer token, kind TEXT, text, source fields, jumpLink)
  A->>A: Same validation and limit check as section 4
  A-->>X: 201 HighlightResponse, or 403 LIMIT_REACHED
  Note over X,R: Access token expired
  X->>A: POST /api/v1/auth/extension/refresh (refreshToken)
  A-->>X: 200 new pair (same rotation rules as the cookies)
```

- The code is 32 random bytes, base64url-encoded. If the tab is signed out, `/extension/connect`
  sends the user to `/login` first and then comes back.
- `ari-api` reads the `ari_access` cookie first, then the `Authorization` header, so every endpoint
  works for the extension.
- The extension calls `ari-api` from its background script, which has host permission for the API,
  so it needs no CORS entry.
- The extension builds `jumpLink` as a text-fragment URL (`#:~:text=`). Clips (F14) use the same
  `POST /highlights` with `kind CLIP` and `contentMd`.

## 7. Paddle checkout and webhook (F15)

```mermaid
sequenceDiagram
  participant B as Browser on /settings
  participant P as Paddle
  participant A as ari-api
  participant D as PostgreSQL
  B->>P: Paddle.js opens the overlay checkout (priceId, customData.userId, email)
  B->>P: Pay
  P-->>B: checkout.completed event in the page
  P->>A: POST /api/v1/billing/paddle/webhook (Paddle-Signature header)
  A->>A: HMAC-SHA256 of ts and the raw body with the webhook secret, reject an old ts
  alt Bad signature
    A-->>P: 401
  else Valid
    A->>D: BEGIN, INSERT processed_events (paddle, event_id) ON CONFLICT DO NOTHING
    alt 0 rows (already processed)
      A->>D: ROLLBACK
      A-->>P: 200, ignored
    else 1 row
      A->>D: Upsert subscriptions, set users.plan from its status, COMMIT
      A-->>P: 200
    end
  end
  loop Every 2 seconds, up to 30 seconds
    B->>A: GET /api/v1/me
    A-->>B: 200 with plan
  end
  B->>A: POST /api/v1/auth/refresh (new JWT with plan PRO)
  Note over B,D: Later, subscription.canceled at the end of the paid period sets plan FREE
```

- Events handled: `subscription.created`, `subscription.updated`, `subscription.canceled`. The first
  one finds the user through `custom_data.userId`; later ones through `paddle_subscription_id`.
- Status to plan: `active`, `trialing` and `past_due` → `PRO`; `paused` and `canceled` → `FREE`.
- The signature header looks like `ts=...;h1=...`. Verify `h1` against HMAC-SHA256 of `ts:rawBody`,
  reject a `ts` older than 5 seconds, and read the raw body before parsing JSON. The endpoint is
  public; the webhook secret comes from the `PADDLE_WEBHOOK_SECRET` environment variable.
- Paddle can send events out of order: skip an event older than `subscriptions.last_event_at`.
- Downgrading deletes nothing. A Free user above 500 keeps every highlight but can't save until under
  500.
- Prices: Pro is $6/month or $48/year, the same everywhere.
