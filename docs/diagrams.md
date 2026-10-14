# Ari diagrams

```mermaid
---
title: "System overview"
---
flowchart LR
  browser["Browser<br/>Ari website"]
  ext["Browser extension<br/>Chrome, Firefox"]
  web["ari<br/>SolidStart server"]
  api["ari-api<br/>Spring Boot"]
  db[("PostgreSQL<br/>data and search")]
  redis[("Redis 8<br/>username Bloom filter, one-time codes,<br/>rate limits, idempotency keys")]
  mq[["RabbitMQ<br/>import.process, email.*"]]
  files[("MinIO<br/>profile photos, uploads, clip images")]
  esp["Email provider<br/>Mailpit locally"]
  paddle["Paddle<br/>billing"]

  browser -- "pages (HTML + cookies)" --> web
  web -- "data while rendering (forwards cookies)" --> api
  browser -- "data after hydration (JSON + cookies)" --> api
  ext -- "saves (JSON + Bearer token)" --> api
  browser -- "images (signed URLs)" --> files
  browser -- "checkout (Paddle.js)" --> paddle
  paddle -- "webhooks" --> api
  api --> db
  api --> redis
  api <-- "publish, consume import.process and email.*" --> mq
  api -- "S3 API" --> files
  api -- "SMTP (mail sender)" --> esp

  classDef later stroke-dasharray: 5 5
  class ext,paddle later
```

```mermaid
---
title: "Background jobs"
---
flowchart LR
  subgraph apiBox["ari-api"]
    tx["Use case<br/>one transaction"]
    pub["Outbox publisher<br/>every second"]
    ic["Import consumer"]
    mail["Mail sender"]
  end
  db[("PostgreSQL<br/>outbox_events")]
  subgraph mqBox["RabbitMQ"]
    ex{{"ari exchange<br/>direct"}}
    qi["import.process"]
    qe["email.transactional"]
    qb["email.bulk"]
    retry["Retry queues<br/>2 s, 10 s, 60 s"]
    dlq["Dead-letter queues<br/>one per queue"]
  end
  files[("MinIO")]
  esp["Email provider"]

  tx -- "business rows + outbox row" --> db
  pub -- "unsent rows" --> db
  pub -- "publish, wait for the confirm" --> ex
  ex --> qi & qe & qb
  qi --> ic
  qe & qb --> mail
  ic -- "read the file" --> files
  mail --> esp
  ic & mail -. "failed, try again" .-> retry
  retry -. "delay over" .-> ex
  ic & mail -. "fourth failure" .-> dlq
```

```mermaid
---
title: "Sequence 1, Register or sign in (F1)"
---
sequenceDiagram
  participant B as Browser
  participant A as ari-api
  participant R as Redis
  participant D as PostgreSQL
  Note over B,D: Typing a username on /register or in Settings › Account (waits 300 ms)
  B->>A: GET /api/v1/auth/username-available?username=Bee.Reader
  A->>R: INCR the caller's rate limit, over 30 a minute is 429 RATE_LIMITED
  A->>A: Check the rule (3-30 letters, digits, _ or .), make it lowercase
  A->>R: BF.EXISTS usernames bee.reader
  alt 0, definitely free
    A-->>B: 200 available true
  else 1, maybe taken
    A->>D: SELECT 1 FROM users WHERE username = 'bee.reader'
    A-->>B: 200 available true or false
  end
  alt Register
    B->>A: POST /api/v1/auth/register (username, password, fullName, birthDate)
    A->>D: Normalize username, hash password, INSERT users ON CONFLICT DO NOTHING (no row is 409 USERNAME_TAKEN)
    A->>R: BF.ADD usernames bee.reader, after the commit
  else Sign in
    B->>A: POST /api/v1/auth/login (username, password)
    A->>D: Find user by username, check password_hash
  end
  A->>A: Create JWT (15 min) and refresh token (30 days, store only its hash)
  A-->>B: Me + Set-Cookie ari_access, ari_refresh (httpOnly)
  B->>A: GET /api/v1/highlights (browser sends the cookies)
  A-->>B: 200 OK + data
```

```mermaid
---
title: "Sequence 2, Token refresh (F1)"
---
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

```mermaid
---
title: "Sequence 3, Server-rendered library page (F2)"
---
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

```mermaid
---
title: "Sequence 4, Save and delete a highlight, free limit (F3, F4)"
---
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

```mermaid
---
title: "Sequence 5, PDF or Word import (F11)"
---
sequenceDiagram
  participant B as Browser
  participant A as ari-api
  participant S as MinIO
  participant D as PostgreSQL
  participant P as Outbox publisher
  participant Q as RabbitMQ
  participant C as Import consumer
  B->>A: POST /api/v1/imports (multipart file)
  A->>A: Check the type (PDF or DOCX) and size, else 400 VALIDATION_FAILED
  A->>S: PUT imports/{userId}/{importId}
  A->>D: One transaction: lock the user row, check the upload space, INSERT imports (QUEUED), INSERT outbox_events (import.process)
  Note over A,S: No space or the transaction fails: DELETE the file, 400 VALIDATION_FAILED
  A-->>B: 202 ImportResponse (QUEUED)
  loop Every second
    P->>D: SELECT unsent rows FOR UPDATE SKIP LOCKED
    P->>Q: Publish import.process (event id = outbox id), wait for the confirm
    P->>D: UPDATE outbox_events SET sent_at
  end
  Q->>C: Deliver import.process
  C->>D: Is the event id in processed_events?
  Note over C,D: Yes: ack and stop (a duplicate delivery)
  C->>D: UPDATE imports SET status READING
  C->>S: GET the file
  C->>C: PDFBox reads PDF annotations, POI reads DOCX highlights and comments, with pages and colors
  alt Marks found
    C->>S: PUT marks/{userId}/{markId}.webp, a picture of each DRAWN or HANDWRITTEN mark
    C->>D: One transaction: INSERT sources (FILE, title and author from the file), INSERT import_marks, status READY, INSERT processed_events
    C->>S: DELETE the file
    C->>Q: ack
  else No marks
    C->>D: One transaction: status EMPTY, INSERT processed_events
    C->>S: DELETE the file
    C->>Q: ack
  else Error
    C->>Q: Reject, retry after 2 s, then 10 s, then 60 s
    Note over C,Q: Fourth failure: status FAILED with error_code, the message goes<br/>to import.process.dlq, the file stays in MinIO for Retry
  end
  loop Every 2 seconds while QUEUED or READING
    B->>A: GET /api/v1/imports/{id}
    A-->>B: 200 ImportResponse (status, and the marks once READY)
  end
  B->>A: PUT /api/v1/imports/{id}/review (title, author, tag, color, ticked mark ids)
  A->>D: One transaction: update the source, INSERT saves for newly ticked marks, DELETE saves of unticked ones, set import_marks.save_id, saves_count, status SAVED
  A-->>B: 200 (added, removed)
```

```mermaid
---
title: "Sequence 6, Save from the extension (F13)"
---
sequenceDiagram
  participant X as Extension
  participant T as Website tab
  participant A as ari-api
  participant R as Redis
  Note over X,R: Connect, on any signed-in page of the website
  T->>X: postMessage signed-in (the user's id)
  X->>T: postMessage want-code (not signed in as that user)
  T->>A: POST /api/v1/me/extension-code (cookies)
  A->>R: Store the code's hash with the user id, 60 s, single use
  A-->>T: 200 with the code
  T->>X: postMessage code
  X->>A: POST /api/v1/auth/extension/token (code)
  A->>R: GETDEL the code
  alt Missing, expired or used
    A-->>X: 401 INVALID_CODE
  else Found
    A->>A: Create JWT and refresh token (client EXTENSION)
    A-->>X: 200 accessToken, refreshToken
    X->>X: Keep the tokens in browser.storage.local
  end
  Note over X,R: Or another account, on the website's /extension/sign-in
  T->>A: POST /api/v1/auth/extension/code (username, password; no cookies change)
  A-->>T: 200 with the code, or 401 INVALID_CREDENTIALS
  T->>X: postMessage account-code
  X->>A: POST /api/v1/auth/extension/token (code), as above
  X->>T: postMessage connected
  Note over X,R: Save a highlight
  X->>A: POST /api/v1/saves (Bearer token, kind HIGHLIGHT, color, text, tags, source)
  A->>A: Same validation and steps as section 4
  A-->>X: 201 SaveResponse
  Note over X,R: Access token expired
  X->>A: POST /api/v1/auth/extension/refresh (refreshToken)
  A-->>X: 200 new pair (same rotation rules as the cookies)
  Note over X,R: Log out on the website (while it follows it), or in the extension
  T->>X: postMessage signed-out
  X->>A: POST /api/v1/auth/extension/logout (refreshToken)
  A-->>X: 204, the extension's session ends
```

```mermaid
---
title: "Sequence 7, Paddle checkout and webhook (F15)"
---
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

```mermaid
---
title: "Sequence 8, Add and verify an email"
---
sequenceDiagram
  participant B as Browser on Settings › Account
  participant A as ari-api
  participant R as Redis
  participant D as PostgreSQL
  participant Q as RabbitMQ
  participant M as Mail sender in ari-api
  participant E as SMTP, Mailpit locally
  B->>A: PUT /api/v1/me/email (email)
  A->>R: INCR the user's send limits, over 1 a minute or 5 a day is 429 RATE_LIMITED
  A->>R: SET code:verify-email:{SHA-256 of a new code} = user id and email, 24 h
  A->>D: One transaction: UPDATE users SET pending_email, INSERT outbox_events (email.transactional, VerifyEmail with the link)
  A-->>B: 202 Me with pendingEmail (200 and nothing sent when it already is the verified email)
  Note over A,Q: The outbox publisher sends it as in section 5
  Q->>M: Deliver VerifyEmail (event id, to, language, link)
  M->>R: SET idem:mail:{event id} sending NX, 1 minute
  alt Already sent
    M->>Q: ack, nothing sent (a duplicate delivery)
  else Claimed
    M->>M: Render the verify template and its texts in the user's language
    M->>E: Send the email, HTML with a plain-text version
    M->>R: SET idem:mail:{event id} sent, 7 days
    M->>Q: ack
  end
  Note over M,E: SMTP fails: delete the idem key, move the message to its next delay queue<br/>(2 s, 10 s, 60 s), then to email.transactional.dlq
  B->>A: POST /api/v1/auth/verify-email (the code from the link, opened on the website)
  A->>R: GETDEL code:verify-email:{SHA-256 of the code}
  alt Missing, expired, or no longer the account's pending email
    A-->>B: 401 INVALID_CODE, the page offers a new link
  else Another account verified this address first
    A-->>B: 409 EMAIL_TAKEN
  else Found
    A->>D: UPDATE users SET email = pending_email, pending_email null, email_verified_at
    A-->>B: 204, the email shows as verified
  end
```

```mermaid
---
title: "Sequence 9, Profile and photo"
---
sequenceDiagram
  participant B as Browser on Settings › Account
  participant A as ari-api
  participant S as MinIO
  participant D as PostgreSQL
  B->>A: PUT /api/v1/me (username, fullName, birthDate)
  A->>D: Another account has the username, in any case? Yes is 409 USERNAME_TAKEN
  A->>D: UPDATE users SET username, full_name, birth_date (the unique key decides a race)
  A-->>B: 200 Me
  B->>B: Crop the picked image to a 512x512 JPEG in the dialog, show it at once
  B->>A: PUT /api/v1/me/photo (multipart photo)
  Note over B,A: Over 2 MB the server answers 413 FILE_TOO_LARGE before ari-api sees it
  A->>A: The first bytes say JPEG, PNG or WebP, else 400 INVALID_PHOTO
  A->>S: PUT avatars/{userId}/{photoId}.jpg
  A->>D: UPDATE users SET avatar_key (fails: DELETE the new file again)
  A->>S: DELETE the old photo, if there was one
  A-->>B: 200 Me with photoUrl, a link signed for an hour
  B->>S: GET photoUrl (the img tag, here and in the sidebar)
  B->>A: DELETE /api/v1/me/photo
  A->>D: UPDATE users SET avatar_key null
  A->>S: DELETE the photo
  A-->>B: 204, the initial shows again
```

```mermaid
---
title: "All tables"
---
erDiagram
  users ||--o{ refresh_tokens : "signs in with"
  users ||--o| subscriptions : "pays with"
  users ||--o{ invoices : "is billed"
  users ||--o{ sources : reads
  users ||--o{ saves : keeps
  users ||--o{ tags : names
  users ||--o{ imports : uploads
  users ||--o{ notes : writes
  sources ||--o{ saves : "saved from"
  saves ||--o{ save_tags : "tagged with"
  tags ||--o{ save_tags : "used by"
  imports |o--o| sources : becomes
  imports ||--o{ import_marks : finds
  import_marks |o--o| saves : "saved as"
  notes ||--o{ note_blocks : "made of"
  saves |o--o{ note_blocks : "quoted in"

  users {
    uuid id PK
    varchar email UK "verified address only, stored lowercase"
    varchar pending_email "waiting for its link, not unique"
    varchar username UK "stored lowercase, 3-30 chars"
    varchar password_hash "bcrypt"
    varchar full_name
    date birth_date
    varchar avatar_key "profile photo in MinIO, null shows the initial"
    varchar plan "NONE until the trial starts, then follows subscriptions"
    int saves_count "the Library count in the sidebar"
    timestamptz email_verified_at "when email was verified"
    timestamptz created_at
    timestamptz updated_at
  }
  refresh_tokens {
    uuid id PK
    uuid user_id FK
    varchar token_hash UK "SHA-256, never the token"
    uuid family_id "chain of rotated tokens"
    varchar client "WEB or EXTENSION"
    timestamptz expires_at
    timestamptz revoked_at "null while valid"
    timestamptz created_at
  }
  subscriptions {
    uuid id PK
    uuid user_id FK, UK "one per user"
    varchar paddle_customer_id
    varchar paddle_subscription_id UK
    varchar status "trialing, active, past_due, paused or canceled"
    varchar billing_period "MONTHLY or YEARLY"
    varchar price_id "the Paddle price"
    timestamptz trial_ends_at "null unless trialing"
    timestamptz current_period_end "next payment"
    timestamptz cancel_at "Hive lasts until then, null unless canceled"
    varchar card_last4
    varchar card_expiry "MM/YY"
    timestamptz last_event_at "skip older webhook events"
    timestamptz created_at
    timestamptz updated_at
  }
  invoices {
    uuid id PK
    uuid user_id FK
    varchar paddle_transaction_id UK
    varchar status "PAID or FAILED"
    int amount_cents
    varchar currency "USD"
    timestamptz billed_at
    timestamptz created_at
  }
  sources {
    uuid id PK
    uuid user_id FK
    varchar kind "WEBSITE or FILE"
    varchar name "site name, or the file name"
    varchar title "page or document title"
    varchar author "optional"
    text url "WEBSITE only, without the fragment"
    tsvector search_vector "generated, F10"
    timestamptz created_at
    timestamptz updated_at
  }
  saves {
    uuid id PK
    uuid user_id FK
    uuid source_id FK
    varchar kind "HIGHLIGHT, CLIP or IMPORT"
    varchar color "YELLOW, GREEN, BLUE, PINK or PURPLE, null for clips"
    varchar clip_type "TABLE or IMAGE, CLIP only"
    text text "the words, or a clip's plain text or caption"
    text content_md "a clipped section as Markdown"
    varchar image_key "a clipped image in MinIO"
    text comment "empty when none"
    varchar section "heading above it on a web page"
    int page_number "page in a file"
    tsvector search_vector "generated, F10"
    timestamptz created_at "saved at, the Library groups by day"
    timestamptz updated_at
  }
  tags {
    uuid id PK
    uuid user_id FK
    varchar name "unique per user in any case"
    timestamptz created_at
  }
  save_tags {
    uuid save_id PK, FK
    uuid tag_id PK, FK
  }
  imports {
    uuid id PK
    uuid user_id FK
    uuid source_id FK, UK "the file's source, set when reading finishes"
    varchar file_name
    varchar content_type
    bigint size_bytes "counts against upload space"
    varchar storage_key "null once the file is deleted"
    varchar status "QUEUED, READING, READY, SAVED, EMPTY, FAILED or DISCARDED"
    int attempts "Retry reads it again"
    varchar error_code "FAILED only"
    timestamptz read_at "reading finished"
    timestamptz reviewed_at "first review saved"
    timestamptz dismissed_at "hidden from the Imports page"
    timestamptz created_at
    timestamptz updated_at
  }
  import_marks {
    uuid id PK
    uuid import_id FK
    uuid save_id FK, UK "null until saved to the Library"
    int position "order in the file"
    varchar type "HIGHLIGHT, UNDERLINE, DRAWN, HANDWRITTEN, STRIKE or NOTE"
    varchar color "one of the five save colors"
    varchar file_color "the file's own color when it was mapped"
    text text
    int page_number
    text comment "the file's comment on the mark"
    varchar image_key "picture of the spot, DRAWN and HANDWRITTEN"
    boolean selected "ticked in the review"
    timestamptz created_at
  }
  notes {
    uuid id PK
    uuid user_id FK
    varchar title "empty until named"
    timestamptz created_at
    timestamptz updated_at "last edit, orders the list"
  }
  note_blocks {
    uuid id PK
    uuid note_id FK
    int position "order in the note"
    varchar kind "TEXT or QUOTE"
    text text "TEXT only"
    uuid save_id FK "QUOTE only"
  }
  outbox_events {
    uuid id PK "also the event id"
    varchar aggregate_type "e.g. IMPORT"
    uuid aggregate_id
    varchar event_type "e.g. ImportRequested"
    varchar routing_key "e.g. import.process"
    jsonb payload
    timestamptz created_at
    timestamptz sent_at "null until published"
  }
  processed_events {
    varchar consumer PK "import.process or paddle"
    varchar event_id PK "outbox id or Paddle event id"
    timestamptz processed_at
  }
```

```mermaid
---
title: "Import statuses"
---
stateDiagram-v2
  [*] --> QUEUED : upload
  QUEUED --> READING : consumer takes it
  READING --> READY : marks found
  READING --> EMPTY : no marks
  READING --> FAILED : error
  FAILED --> READING : Retry
  READY --> SAVED : review saved
  READY --> DISCARDED : Discard
  SAVED --> SAVED : review edited
```

```mermaid
---
title: "Redis and MinIO"
---
flowchart LR
  subgraph pg["PostgreSQL"]
    username["users.username"]
    avatar["users.avatar_key"]
    upload["imports.storage_key"]
    markImage["import_marks.image_key"]
    saveImage["saves.image_key"]
  end
  subgraph redis["Redis 8"]
    bloom["usernames<br/>Bloom filter"]
    codes["code:extension:*<br/>code:verify-email:*"]
    rate["rate:*"]
    idem["idem:mail:*"]
  end
  subgraph files["MinIO, private bucket ari"]
    avatars["avatars/{userId}/{photoId}.jpg"]
    imports["imports/{userId}/{importId}"]
    marks["marks/{userId}/{markId}.webp"]
    saves["saves/{userId}/{saveId}.webp"]
  end
  username -- "BF.ADD, nightly rebuild" --> bloom
  avatar --> avatars
  upload --> imports
  markImage --> marks
  saveImage --> saves
```
