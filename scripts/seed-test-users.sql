-- 50,000 test users for trying the username Bloom filter, paging and search on a local database.
--
--   docker exec -i supabase_db_ari-api psql -U postgres -v ON_ERROR_STOP=1 < scripts/seed-test-users.sql
--
-- They are Supabase Auth users, written straight into auth.users (the profile trigger makes their
-- users rows), so they sign in like any other. Safe to run again: users that already exist are
-- skipped. Every user has the demo user's password (demo1234), a verified <username>@example.test
-- email, and an id starting with 5eed0000-, so they are easy to tell apart and to remove (their
-- profiles go with them):
--
--   DELETE FROM auth.users WHERE id::text LIKE '5eed0000-%';
--
-- The Bloom filter lives in Redis and does not see rows added here. To load them, drop it and
-- restart the API, which fills it again from the users table:
--
--   docker exec ari-redis redis-cli DEL usernames && docker restart ari-api

BEGIN;

-- Username = first + (_ or .) + last + a counter that is left off for the first 1,600, so
-- emma_smith, lucas.green14 ... Each i is a different (first, last, counter), so no two collide.
WITH names AS (
  SELECT
    ARRAY['Aylin','Murad','Leyla','Elvin','Nigar','Rashad','Sevda','Tural','Gunel','Orkhan',
          'Aysel','Farid','Kamran','Narmin','Samir','Ulviyya','Vugar','Zaur','Lala','Emil',
          'Emma','Liam','Olivia','Noah','Sophia','Lucas','Mia','Ethan','Amelia','Mason',
          'Chloe','Logan','Grace','Owen','Nora','Caleb','Ivy','Henry','Ruby','Jack'] AS firsts,
    ARRAY['Aliyev','Mammadov','Huseynov','Hasanov','Guliyev','Ismayilov','Abbasov','Rzayev',
          'Nasirov','Karimov','Jafarov','Valiyev','Mirzayev','Safarov','Sadigov','Ahmadov',
          'Bagirov','Musayev','Najafov','Taghiyev',
          'Smith','Johnson','Garcia','Miller','Brown','Davis','Wilson','Moore','Taylor',
          'Anderson','Thomas','Martin','Lee','Walker','Hall','Young','King','Wright','Scott',
          'Green'] AS lasts
),
generated AS (
  SELECT
    i,
    n.firsts[1 + i % 40] AS first_name,
    n.lasts[1 + (i / 40) % 40] AS last_name,
    i / 1600 AS counter,
    (i / 40 + i / 1600) % 2 AS separator
  FROM generate_series(0, 49999) AS i, names AS n
),
named AS (
  SELECT
    *,
    lower(first_name)
      || CASE separator WHEN 0 THEN '_' ELSE '.' END
      || lower(last_name)
      || CASE counter WHEN 0 THEN '' ELSE counter::text END AS username,
    now() - (50000 - i) * interval '10 minutes' AS created_at
  FROM generated
),
-- One bcrypt hash for all of them: hashing 50,000 times would take an hour.
password AS (
  SELECT extensions.crypt('demo1234', extensions.gen_salt('bf')) AS hash
)
-- Supabase Auth reads these token columns as text, so they are '' rather than NULL.
INSERT INTO auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  confirmation_token, recovery_token, email_change_token_new, email_change,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at
)
SELECT
  '00000000-0000-0000-0000-000000000000',
  ('5eed0000-0000-4000-8000-' || lpad((i + 1)::text, 12, '0'))::uuid,
  'authenticated',
  'authenticated',
  username || '@example.test',
  password.hash,
  created_at,
  '', '', '', '',
  '{"provider": "email", "providers": ["email"]}',
  jsonb_build_object(
    'username', username,
    'full_name', first_name || ' ' || last_name,
    'birth_date', DATE '1960-01-01' + (i * 7919) % 16400),
  created_at,
  created_at
FROM named, password
ON CONFLICT DO NOTHING;

-- What the profile trigger leaves to ari-api: the verified email, the plan and the dates.
UPDATE users
SET email = auth_user.email,
    email_verified_at = auth_user.created_at,
    plan = CASE WHEN right(users.id::text, 1)::int % 10 = 1 THEN 'HIVE' ELSE 'NONE' END,
    created_at = auth_user.created_at,
    updated_at = auth_user.created_at
FROM auth.users AS auth_user
WHERE auth_user.id = users.id AND users.id::text LIKE '5eed0000-%' AND users.email IS NULL;

COMMIT;

ANALYZE auth.users;
ANALYZE users;
SELECT count(*) AS test_users FROM users WHERE id::text LIKE '5eed0000-%';
