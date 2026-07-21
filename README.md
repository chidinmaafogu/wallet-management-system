# Wallet System

A double-entry wallet supporting user onboarding, NUBAN account generation, account funding and
fund transfers between accounts.

Built for the 3Line Limited Senior Backend Engineer assessment, on the provided Spring Boot starter.

**Java 17 · Spring Boot 3.5 · Spring Data JPA · Flyway · PostgreSQL · Redis**

PostgreSQL is the only system of record, in development and in tests alike. Row-level
`SELECT FOR UPDATE` is the mechanism that makes concurrent transfers correct, so the tests exercise
the real thing rather than an in-memory substitute that only approximates it. Redis is a cache in
front of read-heavy endpoints, and nothing depends on it for correctness — the application runs
without it.

---

## Quick start

### With Docker

```bash
docker compose up          # PostgreSQL 16 + Redis 7 + the app, on port 9090
```

`docker compose` starts PostgreSQL, Redis and the application, and supplies every environment
variable, so nothing else is required.

### With a local PostgreSQL

```bash
redis-server --daemonize yes                # or: brew services start redis

psql -d postgres -c "CREATE ROLE wallet LOGIN SUPERUSER PASSWORD 'wallet';"
createdb -O wallet wallet
createdb -O wallet wallet_test             # used by the test suite

cp .env.example .env
set -a && source .env && set +a            # export the variables

./mvnw spring-boot:run
```

Then open **http://localhost:9090/swagger-ui.html**

Flyway creates the schema on first start. The app seeds two funded demo accounts, whose numbers are
printed in the log:

```
Demo data ready: Ada=1000000012 (100000.00), Bola=1000000029 (50000.00)
```

| | |
|---|---|
| Swagger UI | http://localhost:9090/swagger-ui.html |
| OpenAPI JSON | http://localhost:9090/v3/api-docs |
| Health | http://localhost:9090/actuator/health |

### Configuration

**All configuration comes from the environment. There are no defaults compiled into the
application** — a missing variable stops the app at startup with a named error, rather than letting
it quietly connect somewhere unintended. `.env.example` lists every one:

| Variable | Purpose | Example |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/wallet` |
| `DB_USERNAME` / `DB_PASSWORD` | Credentials | `wallet` / `wallet` |
| `DB_POOL_SIZE` | Hikari maximum pool size | `10` |
| `DB_CONNECTION_TIMEOUT_MS` | Connection acquisition timeout | `10000` |
| `CACHE_TYPE` | `redis` in any real deployment; `none` disables caching entirely | `redis` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis connection | `localhost` / `6379` / _(empty)_ |
| `REDIS_TIMEOUT_MS` | Command timeout | `2000` |
| `REDIS_POOL_MAX_ACTIVE` / `REDIS_POOL_MAX_IDLE` / `REDIS_POOL_MIN_IDLE` | Lettuce pool sizing | `16` / `8` / `2` |
| `WALLET_CACHE_TTL_SECONDS` | Cache entry lifetime | `300` |
| `SERVER_PORT` | HTTP port | `9090` |
| `WALLET_INSTITUTION_CODE` | This institution's 6-digit code, used as the NUBAN check-digit seed | `000999` |
| `WALLET_INSTITUTION_NAME` | Display name for house accounts | `3Line Wallet` |
| `WALLET_DEFAULT_CURRENCY` | Currency assigned to new accounts | `NGN` |
| `WALLET_SYSTEM_FUNDING_ACCOUNT` | House account funding is booked against | `0000000000` |
| `WALLET_MIN_TRANSFER_AMOUNT` / `WALLET_MAX_TRANSFER_AMOUNT` | Transfer limits | `0.01` / `1000000.00` |
| `WALLET_LOCK_TIMEOUT_MS` | Row-lock wait before a transfer fails; **keep below `DB_CONNECTION_TIMEOUT_MS`** so lock waits cannot exhaust the pool | `5000` |
| `WALLET_SEED_DEMO_DATA` | Seed two funded demo accounts at startup | `true` |

`.env` is gitignored; only `.env.example` is committed.

### Secrets

No credential in this repository grants access to anything. The values in `.env.example`,
`docker-compose.yml` and the CI workflow belong to disposable local or ephemeral CI databases that
are bound to `localhost`, hold no real data, and are destroyed when the container stops.

The distinction the repository is built around:

- **Configuration** — institution code, currency, transfer limits, pool size. Not sensitive, and
  belongs in version control so a deployment is reproducible and reviewable.
- **Secrets** — real database passwords, signing keys, provider API keys. Never in version control,
  in any form, even for a throwaway environment.

CI reads its database credentials from `secrets.CI_DB_USERNAME` and `secrets.CI_DB_PASSWORD`, with a
non-secret fallback so the workflow still runs on a fork where no secrets are configured. Setting
those two repository secrets is enough to take the fallback out of use entirely.

In production none of this would be a static string. Credentials would come from a managed secret
store — AWS Secrets Manager, GCP Secret Manager or Vault — resolved at startup, with the workload
authenticating by short-lived OIDC federation rather than a long-lived key, and rotated on a
schedule. The application already supports this without a code change, because every setting is read
from the environment with no compiled-in default (see the table above): the secret store populates
the environment, and a missing value stops the application at startup rather than letting it fall
back to something unintended.

## Running the tests

The suite runs against a real PostgreSQL database. **It truncates tables between tests, so point it
at a throwaway database** — never at one holding data you care about.

```bash
createdb -O wallet wallet_test                        # once

export TEST_DB_URL=jdbc:postgresql://localhost:5432/wallet_test
export TEST_DB_USERNAME=wallet
export TEST_DB_PASSWORD=wallet

./mvnw verify                                         # 49 tests
```

Tests run with `CACHE_TYPE=none`, so **Redis is not needed to run them**. Caching is a performance
concern, not a correctness one — the assertions are about money movement, and disabling the cache
keeps the suite deterministic. CI runs the same command against PostgreSQL and Redis service
containers, then boots the application to confirm it starts against both.

---

## A five-minute walkthrough

```bash
BASE=http://localhost:9090/api/v1

# 1. Register two users. Each gets a NUBAN account and a zero-balance wallet.
ADA=$(curl -s -X POST $BASE/users -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Okoro","email":"ada@example.com","phoneNumber":"08031234567"}' \
  | sed -n 's/.*"accountNumber":"\([0-9]*\)".*/\1/p')

BOLA=$(curl -s -X POST $BASE/users -H 'Content-Type: application/json' \
  -d '{"firstName":"Bola","lastName":"Adeyemi","email":"bola@example.com","phoneNumber":"08069876543"}' \
  | sed -n 's/.*"accountNumber":"\([0-9]*\)".*/\1/p')

# 2. Fund Ada. Booked as a movement from the house account, not money from nowhere.
curl -s -X POST $BASE/accounts/$ADA/fund -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"amount":10000.00,"narration":"Opening deposit"}'

# 3. Confirm who owns the destination account before sending.
curl -s $BASE/accounts/$BOLA/name-enquiry

# 4. Transfer.
curl -s -X POST $BASE/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-transfer-1' \
  -d "{\"sourceAccountNumber\":\"$ADA\",\"destinationAccountNumber\":\"$BOLA\",\"amount\":2500.00,\"narration\":\"Rent\"}"

# 5. Send it again with the same key. Same reference, no second debit.
curl -s -X POST $BASE/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-transfer-1' \
  -d "{\"sourceAccountNumber\":\"$ADA\",\"destinationAccountNumber\":\"$BOLA\",\"amount\":2500.00,\"narration\":\"Rent\"}"

# 6. Balances and the ledger behind them.
curl -s $BASE/accounts/$ADA/balance
curl -s "$BASE/accounts/$ADA/statement?size=10"
```

---

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/users` | Register a user, generate an account, provision a wallet |
| GET | `/api/v1/users/{id}` | User with their accounts |
| GET | `/api/v1/accounts/{accountNumber}` | Account detail |
| GET | `/api/v1/accounts/{accountNumber}/name-enquiry` | Resolve the account holder's name |
| GET | `/api/v1/accounts/{accountNumber}/balance` | Current balance |
| GET | `/api/v1/accounts/{accountNumber}/statement` | Ledger history, keyset paginated |
| POST | `/api/v1/accounts/{accountNumber}/fund` | Simulated inbound credit |
| POST | `/api/v1/transfers` | **Transfer between accounts** |
| GET | `/api/v1/transfers/{reference}` | Transaction by reference |
| GET | `/api/v1/transfers/idempotency/{key}` | Resolve a transfer whose outcome is unknown |

Every response uses one envelope:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Transfer successful",
  "data": { "reference": "TRF-20260721-4B8B10A5", "status": "SUCCESS", "amount": 2500.00 },
  "timestamp": "2026-07-21T17:41:05.727697Z",
  "correlationId": "0ad4f6ae"
}
```

Failures carry a machine-readable code and never leak a stack trace:

| Code | HTTP | Trigger |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Field validation failed; per-field detail in `errors` |
| `ACCOUNT_NOT_FOUND` / `USER_NOT_FOUND` / `TRANSACTION_NOT_FOUND` | 404 | Unknown identifier |
| `DUPLICATE_USER` | 409 | Email or phone already registered |
| `INSUFFICIENT_FUNDS` | 422 | Source balance below the amount |
| `ACCOUNT_NOT_ACTIVE` | 422 | Frozen or closed account |
| `SAME_ACCOUNT_TRANSFER` | 422 | Source equals destination |
| `CURRENCY_MISMATCH` | 422 | Accounts hold different currencies |
| `INTER_BANK_NOT_SUPPORTED` | 422 | Destination bank code is not this institution |
| `TRANSFER_LIMIT_EXCEEDED` | 422 | Above the configured maximum |
| `IDEMPOTENCY_KEY_REUSE` | 422 | Same key, different payload |
| `WALLET_LOCKED` | 503 | Lock wait exceeded; safe to retry |

---

## How it works

### Double-entry ledger

A balance is never just a number that gets overwritten. Every movement writes one transaction and a
balanced set of ledger entries that sum to zero and are never mutated.

A transfer of ₦500 from A (holding ₦2,000) to B (holding ₦100):

| account | direction | amount | balance_before | balance_after |
|---|---|---|---|---|
| A | DEBIT | 500.00 | 2000.00 | 1500.00 |
| B | CREDIT | 500.00 | 100.00 | 600.00 |

`wallet_balances` is a performance cache of that history, not the source of truth.
`LedgerInvariantTest` asserts the two never disagree, and that the books sum to zero.

Funding works the same way: it is a transfer from a seeded `SYSTEM` house account, so money never
appears from nowhere and the house account's negative balance mirrors total customer money held.
That is why total balance across every account in the system is always exactly zero.

### Concurrency

Transfers take `PESSIMISTIC_WRITE` row locks on both wallets, **acquired in ascending account-id
order**. That ordering is what prevents a deadlock when A→B and B→A run at the same instant: both
transactions grab the same row first, so one waits instead of the two blocking each other.

Pessimistic rather than optimistic locking is deliberate. A busy merchant wallet under `@Version`
would produce a retry storm where most attempts do work and then discard it; a row lock gives a
bounded wait instead. `@Version` is still present as a second safety net.

`ConcurrentTransferTest` fires 200 simultaneous bidirectional transfers and asserts total money is
unchanged to the kobo, no balance went negative, and every debit is matched by a credit.
It runs against a real PostgreSQL database, so it exercises genuine `SELECT FOR UPDATE` semantics.

### Idempotency, including the unknown outcome

The hard case is not the double click. It is a client that timed out and does not know whether the
money moved.

The receipt and the money movement **commit in the same database transaction**, which buys a
guarantee worth more than any retry logic:

> If no transaction record exists for an idempotency key, the transfer did not happen, and it is
> safe to retry.

So a client that generated the key can always resolve its own uncertainty:

```
GET /api/v1/transfers/idempotency/{key}
  200 -> here is the outcome. Do not retry.
  404 -> it did not happen. Safe to retry with the same key.
```

Other properties:

- The key is stored under a **unique index**, so the guarantee is enforced by the database, not by
  an application check that would itself race. Two genuinely concurrent duplicates are arbitrated by
  the index; one commits and the other returns the committed receipt.
- Requests are **fingerprinted**. Reusing a key with a different amount returns
  `IDEMPOTENCY_KEY_REUSE` rather than silently replaying, which would mask a real second payment.
- **Declines are idempotent too.** Retrying a key that was declined returns the decline, not a fresh
  attempt. A genuinely new attempt needs a new key.

### Atomicity

Money movement is wrapped in `@Transactional(rollbackFor = Exception.class)`. Spring's default rule
rolls back only on unchecked exceptions, which would let a debit commit while its credit was lost —
so the rule is widened explicitly, and every business failure is a `RuntimeException`. The
transactional boundary lives in `LedgerService` and is always invoked across beans, never by
self-invocation, which would silently bypass the proxy entirely.

`TransactionRollbackTest` forces a failure partway through writing the ledger and asserts both
balances are unchanged, no ledger entries survive, and no transaction row is left behind.

### Failed transfers are recorded, with the reason

A decline is not a silent rejection — it is persisted:

| reference | status | amount | failure_reason |
|---|---|---|---|
| `TRF-20260721-374B29C7` | FAILED | 999999.00 | `INSUFFICIENT_FUNDS` |

The reason is a machine-readable code so declines can be aggregated and alerted on. A spike in
`INSUFFICIENT_FUNDS` is a product signal; a spike in `ACCOUNT_NOT_ACTIVE` is an operations incident.
Neither is visible in a system that only records successes. The row is written in its own
transaction (`REQUIRES_NEW`) so it survives the rollback of the transfer that produced it, and no
ledger entries are written because no money moved.

### Account numbers: NUBAN

10-digit account numbers are generated using the CBN NUBAN check-digit algorithm — a 9-digit serial
drawn from a database sequence, with the check digit computed over the institution code and serial
using the repeating `3,7,3` weight vector.

The implementation is verified against a published vector (GTBank `058`, serial `001656322`, real
NUBAN `0016563228`). A 13-digit variant of the algorithm that circulates in several blog posts gives
the wrong check digit for that input and is rejected by the test suite.

A sequence makes uniqueness structural rather than generate-and-retry-on-collision, and the check
digit catches a mistyped account number before it reaches the database.

### A NUBAN is only unique within an institution

The CBN standard specifies that the 10-digit NUBAN is unique **within each institution**, not
globally — two banks can issue the same ten digits. So an account's identity is the composite
`(institution_code, account_number)`, and that is what the unique index enforces. Looking an account
up by number alone is not possible, because the index does not support it.

This is also why `POST /transfers` accepts an optional `destinationBankCode`: omitted or matching
ours means an internal transfer, anything else is refused with `INTER_BANK_NOT_SUPPORTED` rather
than being silently treated as internal.

### Extensibility

Transaction types are strategies resolved through a factory, so adding withdrawal, reversal or fee
handling means adding one `@Component` and editing nothing:

```java
public interface TransactionProcessor {
    TransactionType supports();
    void validate(TransactionContext context);
    List<LedgerLeg> buildLegs(TransactionContext context);
}
```

`LedgerService.post(...)` takes a **list of legs** and asserts only that debits equal credits, so it
already handles a two-leg transfer and a three-leg fee split with no new code:

```
transfer:  DEBIT A 1000.00   CREDIT B 1000.00                       -> sums to zero
with fee:  DEBIT A 1000.00   CREDIT B  990.00   CREDIT fees 10.00   -> sums to zero
```

That zero-sum assertion is the single invariant making every future transaction type safe by
construction.

### Money

`BigDecimal` throughout, stored as `NUMERIC(19,4)`. Never a float. The API accepts two decimal
places and **rejects** greater precision rather than silently rounding it.

### Caching

Redis, shared across every application instance. The governing rule:

> **A cache may only make the system fail faster. It may never make it succeed wrongly.**

| Data | Cached | Why |
|---|---|---|
| Name enquiry | **Yes**, 5 min | The highest-volume read in the system — a client resolves a name before every transfer, often several times while a user types |
| Account detail | **Yes**, 5 min | Read-heavy, and changes rarely |
| **Balance used to authorise a debit** | **Never** | Read from the primary, under a row lock, inside the transaction |

The last row is the important one. A cached balance behind a debit decision is how double-spends
get built, so the *authorization balance* and the *display balance* are treated as different things:
the balance endpoint reads the database every time.

Caching an account's `status` is still safe even though a frozen account could briefly appear
`ACTIVE` in the cache, because `LedgerService` re-reads the status from the locked row inside the
transaction before moving any money. The cache can therefore only reject a request earlier than the
database would — never approve one it shouldn't.

Redis rather than an in-process cache is a deliberate choice for horizontal scale. An in-process
cache is per-instance: with N instances you get N cold caches, N times the database load on a
restart, and instances disagreeing with each other. The cost is a network hop per lookup, which is
the right trade at this read volume but would not be for something called once per request.

**Redis being down does not take the wallet down.** A `CacheErrorHandler` degrades every cache
failure — read, write, evict, clear — to a logged warning and a database read. Spring's default is
to propagate the exception, which would turn a cache outage into a full outage.

Two further details for high volume: `@Cacheable(sync = true)` collapses concurrent misses on the
same key so a cold hot key produces one database query rather than hundreds, and values are
serialised as JSON with a type allow-list restricted to this application's own packages, since
unrestricted polymorphic deserialisation from a cache is a known remote-code-execution vector if the
store is ever compromised.

---

## Design decisions

| Decision | Why |
|---|---|
| Double-entry ledger rather than a mutable balance | Auditability and reconciliation; a balance column alone cannot explain itself or prove it is right |
| Pessimistic locking | Bounded waits instead of retry storms on hot wallets |
| Ascending account-id lock ordering | Prevents deadlock on simultaneous A→B and B→A |
| Receipt and money commit together | Makes "no record means it did not happen" a provable guarantee |
| Flyway migrations, `ddl-auto: validate` | The schema is versioned; `validate` fails fast on drift that `update` would hide |
| Uniqueness enforced by database indexes | An application-level `existsBy` check before insert is itself a race |
| PostgreSQL | Row-level `SELECT FOR UPDATE` *is* the correctness mechanism; exact `NUMERIC`; constraints as a last line of defence |
| PostgreSQL in tests too, not an in-memory substitute | An in-memory database approximates `SELECT FOR UPDATE` rather than implementing it, so a green suite against one would not prove the concurrency behaviour this system depends on |
| Redis rather than an in-process cache | An in-process cache is per-instance: N instances mean N cold caches, N times the database load after a deploy, and instances disagreeing with each other |
| Cache failures degrade to a database read | Spring propagates cache exceptions by default, which would turn a Redis restart into a wallet outage |
| Request DTOs, never entities, at the API boundary | Avoids mass assignment |
| Keyset pagination on statements | `OFFSET 50000` makes PostgreSQL walk and discard 50,000 rows |

---

## Assumptions

1. **Single currency (NGN).** Currency is modelled and mismatches rejected; FX is out of scope.
2. **One account per user at registration**, though the schema is one-to-many.
3. **Funding is simulated** — there is no payment provider integration. It is modelled honestly as a
   double-entry movement from a house account rather than faked by incrementing a balance.
4. **"Transfer" means wallet-to-wallet within this institution.** A different bank code is rejected
   explicitly; inter-bank settlement is out of scope.
5. **No overdrafts** for customer accounts. Only the `SYSTEM` house account may go negative, which is
   what a liability account is for.
6. **Amounts are accepted to 2 decimal places**; greater precision is rejected, not rounded.
7. **No authentication** — see below.
8. **Account numbers are unique per institution, not globally**, per the CBN standard. The schema
   reflects this from day one even though only one institution exists today.

## Out of scope, and what production would need

Deliberate omissions, kept out to hold the brief's "simple" scope:

| Excluded | What production would do |
|---|---|
| **Authentication and authorisation** | **The first thing I would add.** JWT plus ownership checks so you can only debit an account you own |
| Real payment-provider funding | Provider adapter with a signed webhook confirming settlement |
| Reversals and refunds | Reversing ledger entries — never `UPDATE` or `DELETE` on the original |
| Multi-currency and FX | Currency is modelled; cross-currency transfers are rejected rather than converted |
| KYC tiers and per-tier limits | Only a flat configurable maximum is enforced today |
| Notifications and webhooks | Transactional outbox, so events survive a rollback and slow calls never hold a wallet lock |
| Rate limiting | Gateway level, plus per-account velocity checks |
| Scheduled reconciliation | Nightly ledger-versus-balance sweep with alerting |
| Ledger partitioning | Monthly range partitions once the table passes ~100M rows |
| House-account sharding | Every funding operation locks the same house-account row; at high volume it would be split into N sub-accounts by hash |

## Deviations from the provided skeleton, and why

The starter contained three entities with no relationships between them, three repositories, one
DTO, and a service interface with two empty methods. Changes made, and the reason for each:

| Change | Why |
|---|---|
| `User`, `Account`, `WalletBalance` given real relationships | The three tables had no foreign keys, so "what is this user's balance?" was unanswerable |
| Added `WalletTransaction` and `LedgerEntry` | The starter had no transaction history at all |
| `DoTransDto` → `TransferRequest` | Meaningful name, bean validation, explicit currency and optional bank code |
| `createUserAndAccount(User)` → `createUserAndAccount(CreateUserRequest)` | Taking a JPA entity from an HTTP body is a mass-assignment hole |
| `doIntraTransfer` returns a receipt instead of `void` | The caller had nothing to reconcile against, and nothing to query after a timeout |
| `ServiceCall`/`DoService` → `UserAccountService`, `TransactionService`, `LedgerService` | Names that describe responsibilities; exactly one class is permitted to touch a balance |
| Added `@Transactional` | The starter had none, so a debit could commit without its credit |
| `ddl-auto=update` → Flyway with `validate` | Versioned schema, and drift fails the build instead of being silently applied |
| `application.properties` → `application.yml` + a `postgres` profile | Profile-specific configuration |
| Added controllers, exception handling, OpenAPI, tests, Docker, CI | The starter had none of these |

The base package `com.example.test` and the Maven coordinates were kept as provided.

---

## Project layout

```
src/main/java/com/example/test
├── config/          WalletProperties, OpenAPI, Redis cache, demo seeder
├── common/          ApiResponse envelope, correlation-id filter, Money
├── controller/      Users, Accounts, Transfers
├── dto/             Request and response records
├── exception/       ErrorCode, WalletException, @RestControllerAdvice
├── model/           Entities and enums
├── repo/            Repositories, including the pessimistic-lock query
└── service/
    ├── LedgerService              the only code that locks wallets and moves money
    ├── DefaultTransactionService  idempotency and orchestration
    ├── UserAccountService         onboarding and queries
    ├── NubanGenerator             CBN check-digit algorithm
    └── processor/                 TransactionProcessor strategies and factory

src/main/resources/db/migration    V1 core tables · V2 ledger · V3 house account
```

`DESIGN.md` in this repository has the fuller design write-up: the skeleton audit, the scale and
failure-mode analysis, the full race-condition inventory, and the indexing rationale.

## Tests

| Test | What it proves |
|---|---|
| `ConcurrentTransferTest` | 200 simultaneous bidirectional transfers conserve every kobo; no deadlock; no overdraw, under real PostgreSQL row locks |
| `IdempotencyTest` | Replay returns the original receipt; no double debit; key reuse with a changed payload rejected; unknown outcome resolvable |
| `TransactionRollbackTest` | A failure mid-ledger reverts everything; unbalanced legs write nothing |
| `LedgerInvariantTest` | Books sum to zero; cached balance equals the ledger for every account |
| `NubanGeneratorTest` | Check digit against the published CBN vector; the wrong variant rejected |
| `AccountResolutionTest` | The same NUBAN under two institution codes is two different accounts |
| `TransferIntegrationTest` | Full HTTP surface: happy path, declines, validation, name enquiry, statement |

The concurrency, idempotency and rollback tests are the ones worth reading first.
