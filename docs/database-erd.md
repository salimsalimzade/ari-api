# Ari database: every table in one diagram

Last updated 2026-10-12. The same tables as [database.md](database.md), in one Mermaid block to
copy at once (for example into https://mermaid.live). Column rules, indexes and changesets are in
database.md; keep the two files in step.

```mermaid
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
