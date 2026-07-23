# Wallet System — Design Document

**Assessment:** 3Line Limited, Senior Backend Engineer
**Stack:** Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL
**Status:** design agreed, implementation pending

This document is the thinking behind the code. It records what the provided skeleton
gets wrong, the design chosen to fix it, and — just as importantly — what was
deliberately left out.

---

## 1. Reading the brief

The stated requirements are small:

1. Create a user and generate an account
2. Fund transfer between accounts

The provided starter is a Spring Boot project containing three entities, three
repositories, one DTO and a service interface with two empty method bodies. There
are no controllers and no relationships between the entities.

So the exercise is not "can you write two methods." It is **can you tell the
difference between a wallet that appears to work and a wallet that is correct
under concurrency and failure, and can you prove it.** A transfer endpoint that
passes a single manual happy-path test is easy. One that cannot lose money when
two requests hit the same wallet at the same instant, that can tell a client whose
connection dropped whether their money moved, and that can explain where every
kobo went six months later, is the actual job.

Three things carry the submission:

| | Why it matters |
|---|---|
| **Correctness under concurrency and failure** | The bugs in this domain are silent, unrecoverable and career-defining |
| **Auditability** | A payments company cannot run on a mutable `balance` column with no history |
| **Reviewer experience** | A reviewer has ~20 minutes. Clone → run → understand must be frictionless |

---

## 2. Audit of the provided skeleton

Every item below is a real defect in the starter, not a stylistic preference.

### 2.1 The domain model has no relationships

```java
@Entity class Account       { Long id; String accountNumber; }   // no link to User
@Entity class WalletBalance { Long id; BigDecimal amount; }      // no link to Account
@Entity class User          { Long id; String email; }           // no name, no phone
```

Three orphan tables. There is no way to answer *"what is this user's balance?"*
because nothing joins. This must be fixed before a single line of business logic
can be written.

### 2.2 There is no ledger — the fatal one

The starter models a balance as a number you overwrite. That means no history of
how a balance was reached, no way to reconcile or dispute, and no way to detect
that money was lost — because the evidence is overwritten by the bug.

Real wallet systems are **double-entry**: every movement writes a balanced set of
entries that sum to zero and are never mutated. The balance column survives only
as a performance cache whose correctness is continuously provable against the
ledger.

### 2.3 No transaction boundary

`doIntraTransfer` has no `@Transactional`. The debit and the credit must both
commit or both roll back. Without it, a failure between the two destroys money.
Covered in depth in §10.

### 2.4 Lost updates under concurrency

```java
// what the naive implementation looks like
WalletBalance from = repo.findById(x);          // reads 1000
from.setAmount(from.getAmount().subtract(100)); // computes 900 in the JVM
repo.save(from);                                // writes 900
```

Two concurrent transfers out of the same wallet both read 1000, both write 900,
and 100 is created from nothing. This must be fixed **and demonstrated by a test**,
not asserted in a README. See §11.

### 2.5 No idempotency

Client posts a transfer, the connection times out, client retries, customer is
debited twice. See §12 — this is the requirement with the most subtlety in it.

### 2.6 `void` return on a money operation

```java
void doIntraTransfer(DoTransDto request);
```

No reference, no status, no timestamp. The caller has nothing to reconcile against
or show the customer — and critically, nothing to *query with* after a timeout.

### 2.7 Entity accepted at the API boundary

```java
void createUserAndAccount(User user);   // User is a JPA entity
```

Binding an HTTP body onto an entity is a mass-assignment hole: the client can set
`id`, or any field added later. The boundary takes a request DTO.

### 2.8 Smaller but real

| Issue | Fix |
|---|---|
| `spring.jpa.hibernate.ddl-auto=update` | Flyway migrations, `ddl-auto: validate` |
| `User` maps to table `user` — reserved word in PostgreSQL | `@Table(name = "users")` |
| No bean validation anywhere | `@Valid` + constraints on request DTOs |
| No exception handling — stack traces leak to clients | `@RestControllerAdvice` |
| No controllers at all | REST layer with OpenAPI |
| `DoService`, `ServiceCall`, `DoTransDto` carry no meaning | renamed, §4 |
| `open-in-view` defaulting to true | disabled |

---

## 3. Scope guardrails

The brief says *simple*. Depth on the two required flows beats breadth. Written
down so scope creep is a visible decision rather than an accident.

**In scope:** user registration with automatic account + wallet provisioning;
NUBAN account number generation; account funding; intra-wallet transfer;
double-entry ledger and statement; idempotent, concurrency-safe money movement;
validation, structured errors, OpenAPI, tests, Docker, CI.

**Deliberately out of scope** — each a considered *no*, with the production answer:

| Excluded | Production answer |
|---|---|
| Authentication / authorisation | JWT + resource ownership checks; **this is gap #1** and stated as such |
| Real PSP / bank funding integration | Provider adapter + signed webhook to confirm settlement |
| Reversals, refunds, chargebacks | Reversing ledger entries — never `UPDATE`/`DELETE` on the original |
| Multi-currency and FX | Currency is modelled; cross-currency transfers are rejected, not converted |
| KYC tiers and per-tier limits | Only a flat configurable maximum is enforced |
| Notifications / webhooks | Transactional outbox → broker, so events survive a rollback (§10.4) |
| Rate limiting | Gateway-level, plus per-account velocity checks |
| Scheduled reconciliation job | Nightly ledger-vs-balance sweep and alert |

The line: **anything that changes how money moves is in scope; anything that
surrounds it is out.**

---

## 4. Skeleton rework

Skeleton DTOs and services are brought up to standard rather than preserved.

### DTOs

| Before | After | Reason |
|---|---|---|
| `DoTransDto { fromAccount, toAccount, amount }` | `TransferRequest { sourceAccountNumber, destinationAccountNumber, destinationBankCode?, amount, currency, narration }` | Meaningful name; `@NotBlank`/`@DecimalMin`/`@Digits(2)` validation; explicit currency so a mismatch is rejected rather than assumed; optional bank code because a NUBAN is only unique within an institution (§6) |
| *(none)* | `CreateUserRequest`, `FundAccountRequest` | Never bind HTTP bodies onto entities |
| *(none)* | `CreateUserResponse`, `TransferResponse`, `AccountResponse`, `BalanceResponse`, `StatementEntryResponse` | Entities are never serialised out — no lazy-loading leaks, no accidental exposure of internals |
| *(none)* | `ApiResponse<T>` envelope | One consistent shape for success and failure |

### Services

`ServiceCall` / `DoService` are replaced by services with real responsibilities.
Note the third: **exactly one class is allowed to touch a balance.**

| Service | Responsibility |
|---|---|
| `UserAccountService` | Register user, provision account + zero-balance wallet, read account/balance/statement |
| `TransactionService` | Validation, idempotency, orchestration; delegates type-specific rules to a processor (§5) |
| `LedgerService` | `post(...)` — the *only* code that locks wallet rows, writes ledger entries and updates balances |

```java
public interface TransactionService {
    // Idempotency key comes from the HTTP header, not the body (Stripe convention).
    TransferResponse transfer(TransferRequest request, String idempotencyKey);
    TransferResponse fund(String accountNumber, FundAccountRequest request, String idempotencyKey);
}
```

Every rename goes in a README section titled *Deviations from the provided
skeleton, and why* — a reviewer should never have to guess whether a change was
intentional.

---

## 5. Extensibility: factory + strategy, open for extension

Two transaction types already exist (transfer and funding), and withdrawal, fee
and reversal are obvious successors. Two real implementations is what makes this
abstraction *earned* rather than speculative — with one implementation it would be
premature.

```java
public interface TransactionProcessor {
    TransactionType supports();
    void validate(TransactionContext ctx);          // type-specific rules
    List<LedgerLeg> buildLegs(TransactionContext ctx);  // must sum to zero
}
```

Implementations: `TransferProcessor`, `FundingProcessor`.

The factory is the idiomatic Spring form — no `switch`, no `if/else` chain:

```java
@Component
public class TransactionProcessorFactory {
    private final Map<TransactionType, TransactionProcessor> processors;

    // Spring injects every implementation on the classpath.
    public TransactionProcessorFactory(List<TransactionProcessor> found) {
        this.processors = found.stream()
            .collect(toUnmodifiableMap(TransactionProcessor::supports, identity()));
    }

    public TransactionProcessor forType(TransactionType type) { ... }
}
```

Adding `WithdrawalProcessor` later means adding one `@Component`. Nothing existing
is edited — **open for extension, closed for modification**, demonstrated rather
than asserted.

The payoff compounds in the ledger. Because `LedgerService.post(...)` accepts a
**list of legs** and asserts only that debits equal credits, it handles a two-leg
transfer and a three-leg fee split with no new code:

```
transfer:   DEBIT A 1000.00   CREDIT B 1000.00                        -> sums to 0
with fee:   DEBIT A 1000.00   CREDIT B  990.00   CREDIT fees 10.00    -> sums to 0
```

The zero-sum assertion is the single invariant that makes every future
transaction type safe by construction.

---

## 6. Target domain model

```mermaid
erDiagram
    USERS ||--o{ ACCOUNTS : owns
    ACCOUNTS ||--|| WALLET_BALANCES : "has cached"
    ACCOUNTS ||--o{ LEDGER_ENTRIES : "is posted to"
    WALLET_TRANSACTIONS ||--|{ LEDGER_ENTRIES : "balances to zero"

    USERS { bigint id PK  string email UK  string phone_number UK  string first_name  string last_name }
    ACCOUNTS { bigint id PK  string institution_code UK  string account_number UK  bigint user_id FK  string account_type  string currency  string status }
    WALLET_BALANCES { bigint id PK  bigint account_id FK-UK  decimal balance  bigint version }
    WALLET_TRANSACTIONS { bigint id PK  string reference UK  string idempotency_key UK  string request_hash  string status  decimal amount }
    LEDGER_ENTRIES { bigint id PK  bigint transaction_id FK  bigint account_id FK  string direction  decimal amount  decimal balance_before  decimal balance_after }
```

**Money type:** `BigDecimal` mapped to `NUMERIC(19,4)`. Never `double`. Two decimal
places accepted at the API — more precision is **rejected, not silently rounded**.
Quietly rounding someone's money is how you end up in a reconciliation meeting.

### Account numbers: NUBAN

Researched against the CBN standard rather than assumed, because it changes the
schema. Sources: the [2010 NUBAN proposal][nuban2010] and the [2020 revised
standard][nuban2020] extending NUBAN to Other Financial Institutions.

```
 institution code | account serial | check       16-digit internal seed
    6 digits      |    9 digits    |   1
                  |<---- customer-facing NUBAN: 10 digits ---->|
```

- Deposit money banks' 3-digit codes are left-padded to 6: `058` → `000058`.
  OFIs (microfinance banks, PSBs, mobile money operators) use the wider range.
- Check digit: weights `[3,7,3]` repeated across the 15-digit code+serial seed,
  sum the products, `10 - (sum mod 10)`, and `10` becomes `0`.

**Verified against a published vector** rather than trusted from memory — GTBank
(`058`), serial `001656322`, real NUBAN `0016563228`:

| Interpretation | Seed | Sum | Check digit | |
|---|---|---|---|---|
| 2010 spec, 3-digit code | 12 digits | 162 | **8** | correct |
| 2020 spec, 6-digit padded | 15 digits | 162 | **8** | correct |
| 4-digit padding (circulates in several blog posts) | 13 digits | 150 | 0 | **wrong** |

The third row matters: that variant appears in more than one widely-shared write-up
and would have shipped a validator that silently rejects valid account numbers.

A pleasing property of the revision: padding to 6 digits shifts the seed by exactly
one full weight period, so the 2020 form yields identical check digits to the 2010
form. The standard is backward compatible by construction, not by special-casing.

### The correction this forces on the schema

The CBN standard states the 10-digit NUBAN is unique **within each deposit-taking
institution** — not globally. Two institutions can and do issue the same ten
digits, and the check digit does not prevent it: two different institution codes
collide mod 10 roughly one time in ten.

So `UNIQUE (account_number)` in my earlier draft was **wrong**. An account's
identity is the composite:

```sql
UNIQUE (institution_code, account_number)
```

Consequences that follow:

- `accounts` carries `institution_code`. Ours comes from configuration, and the
  column exists from day one so external beneficiary accounts can be stored later
  without a migration.
- An account is **always resolved by the pair**, never by number alone. A lookup
  by number alone is a latent bug the moment a second institution appears.
- `TransferRequest` carries an optional `destinationBankCode`. Absent or equal to
  ours → intra-wallet transfer (in scope). Different → inter-bank, rejected with
  `INTER_BANK_NOT_SUPPORTED` rather than silently treated as internal. This is
  what makes "intra transfer" a stated guarantee instead of an assumption.
- It is also why NIP name-enquiry takes bank code *and* account number: the number
  alone does not identify an account in Nigeria.

Generation still draws the 9-digit serial from a database sequence, so uniqueness
within our institution is structural rather than generate-and-retry-on-collision,
and the check digit catches a mistyped number locally before it reaches the
database.

[nuban2010]: https://www.cbn.gov.ng/OUT/2010/CIRCULARS/BSPD/NUBAN%20PROPOSAL%20-%2020091010%20_FINAL%20UPLOAD_.PDF
[nuban2020]: https://www.cbn.gov.ng/out/2020/psmd/revised%20standards%20on%20nigeria%20uniform%20bank%20account%20number%20(nuban)%20for%20banks%20and%20other%20financial%20institutions%20.pdf

**The house account:** funding is not money materialising from nothing. It is a
transfer from a seeded `SYSTEM` account, so funding is the same balanced
double-entry as anything else, and that account's negative balance mirrors total
customer money held — the institution's liability.

---

## 7. Why PostgreSQL

A decision, not a default.

- **Row-level locking is the correctness mechanism.** `SELECT ... FOR UPDATE` is
  what makes a concurrent transfer safe. On a document store without cross-document
  transactions you would rebuild ACID in application code, badly.
- **Exact decimal arithmetic.** `NUMERIC` is exact; there is no float anywhere near
  a balance.
- **Constraints as the last line of defence.** Unique indexes on email, phone,
  account number and idempotency key; foreign keys; `CHECK (amount > 0)`. The
  database refuses bad data even if a future code path forgets to.
- **Mature partitioning** for the ledger-growth problem in §14.
- **Operational maturity**: `pg_stat_statements`, `EXPLAIN (ANALYZE, BUFFERS)`,
  logical replication, PgBouncer.

**No in-memory substitute, including in tests.** An embedded database was
considered for zero-setup review, and rejected: it *approximates* `SELECT FOR
UPDATE` rather than implementing PostgreSQL's semantics, so a green suite against
one would not prove the single property this system most depends on. The suite
runs against a real PostgreSQL database locally and against a service container in
CI. The cost is that a reviewer needs PostgreSQL or Docker; that is the right
trade for a system whose correctness argument is row-level locking.

One operational note for later: if PgBouncer is introduced in transaction-pooling
mode, session-scoped features break. Nothing in this design relies on them, which
is deliberate.

---

## 8. Indexing strategy

Indexes are designed against the actual query set, because on the write-heavy
tables **every index is a tax on every transfer.**

| Table | Index | Serves |
|---|---|---|
| `users` | UNIQUE `(email)`, UNIQUE `(phone_number)` | Registration uniqueness — enforced by the DB, not a racy pre-check |
| `accounts` | UNIQUE `(institution_code, account_number)` | Every transfer resolves two accounts; the hottest lookup in the system. **Composite, not `account_number` alone** — a NUBAN is only unique within an institution (§6) |
| `accounts` | `(user_id)` | "My accounts", and the FK note below |
| `wallet_balances` | UNIQUE `(account_id)` | Locked row lookup; uniqueness also forbids two wallets on one account |
| `wallet_transactions` | UNIQUE `(reference)` | Receipt lookup |
| `wallet_transactions` | UNIQUE `(idempotency_key)` | **The idempotency guarantee itself** |
| `wallet_transactions` | `(source_account_id)`, `(destination_account_id)` | FK indexes |
| `ledger_entries` | `(account_id, created_at)` | Account statement, newest-first |
| `ledger_entries` | `(transaction_id)` | Fetching both legs of a transaction |

Points that matter:

**The account index is deliberately composite.** `(institution_code,
account_number)` also serves lookups by institution alone via the leftmost prefix,
and — more importantly — makes it *impossible* to write the racy
"find by account number only" query, because the unique index does not support it.
The schema enforces the rule rather than relying on reviewers to catch it.

**PostgreSQL does not auto-index foreign keys.** MySQL does; Postgres does not.
Missing FK indexes make parent-row operations and referential checks scan, and
show up as mysterious lock contention. Every FK above is explicitly indexed.

**Composite column order is not arbitrary.** `(account_id, created_at)` serves both
"everything for this account" and "recent activity for this account" via the
leftmost-prefix rule. `(created_at, account_id)` would serve neither well.

**Low-cardinality columns are not indexed alone.** `status`, `currency` and
`direction` have a handful of distinct values; a standalone index on them is worse
than a scan. They earn their place only inside a composite or a **partial index** —
e.g. `WHERE status = 'FAILED'` for an operations dashboard, which stays tiny even
as the table reaches hundreds of millions of rows.

**The idempotency index is a known write hotspot.** Random UUIDs insert randomly
into a B-tree, causing page splits and WAL churn. Acceptable at the scale in §14;
the escalation path is a hash index, since the only access pattern is equality.

**Deep pagination is a trap on statements.** `OFFSET 50000` makes PostgreSQL walk
and discard 50,000 rows. Statements use **keyset pagination**
(`WHERE (created_at, id) < (:cursor)`) so page 1000 costs the same as page 1.

**Under-indexing is a real option.** `ledger_entries` is the highest-write table in
the system. It gets exactly the two indexes above and no more; any further index
would be paid for on every single transfer.

---

## 9. How a transfer executes

Worked example: **A → B, ₦500.00**, A holding ₦2,000, B holding ₦100.

One `wallet_transactions` row:

| reference | status | amount | source | destination |
|---|---|---|---|---|
| `TRF-20260721-3F9A2C` | SUCCESS | 500.00 | A | B |

Two `ledger_entries` that **sum to zero**:

| account | direction | amount | balance_before | balance_after |
|---|---|---|---|---|
| A | DEBIT | 500.00 | 2000.00 | 1500.00 |
| B | CREDIT | 500.00 | 100.00 | 600.00 |

```
 1. Validate request shape                 -> 400, nothing persisted
 2. Idempotency fast path                  -> terminal record? return it (§12)
 3. Resolve both (institution, number)     -> 404 unknown, 422 foreign institution
 4. Reject source == destination, currency mismatch
 5. BEGIN TRANSACTION
 6.   Lock both wallet rows FOR UPDATE, ordered by ascending account id
 7.   Re-read account status under lock     -> 422 if frozen (TOCTOU, §14)
 8.   Assert source balance >= amount       -> else FAILED + reason, throw 422
 9.   Insert transaction row (carries the idempotency key)
10.   Insert ledger legs from the processor; assert debits == credits
11.   Update both cached balances
12. COMMIT                                  <- receipt and money commit together
13. Return the receipt
```

Steps 5–12 are one atomic unit, and step 12 is why the in-doubt story in §12
works. Nothing outside `LedgerService` may execute steps 6–11.

### Failed transfers are recorded with the reason

A decline is not a silent rejection. Step 8 persists a real transaction row:

| reference | status | amount | source | destination | failure_reason |
|---|---|---|---|---|---|
| `TRF-20260721-8B41D0` | FAILED | 5000.00 | A | B | `INSUFFICIENT_FUNDS` |

`failure_reason` stores the machine-readable code (`INSUFFICIENT_FUNDS`,
`ACCOUNT_NOT_ACTIVE`, `TRANSFER_LIMIT_EXCEEDED`), not free text, so declines can be
aggregated and alerted on. A spike in `INSUFFICIENT_FUNDS` is a product signal; a
spike in `ACCOUNT_NOT_ACTIVE` is an operations incident — and neither is visible in
a system that records only successes.

The row is written via `REQUIRES_NEW` (§10.5) so it survives the rollback of the
transfer that produced it, and no ledger entries are written, because no money
moved. It carries the idempotency key, so retrying that key returns the decline
and its reason rather than silently re-attempting (§12).

---

## 10. Atomicity: debit succeeds, credit fails, revert everything

The requirement is one line. The ways Spring silently breaks it are not.

### 10.1 Checked exceptions do not roll back

Spring's default rollback rule is `RuntimeException` and `Error` **only**. A
checked exception thrown after the debit lets the transaction **commit the debit**
and lose the credit — exactly the failure being guarded against.

Mitigation: all business failures extend a `RuntimeException` base
(`WalletException`), and the boundary is annotated
`@Transactional(rollbackFor = Exception.class)` so the rule holds even if someone
later introduces a checked exception.

### 10.2 Self-invocation bypasses the proxy

```java
public void transfer(...) {
    this.doTransfer(...);   // @Transactional here does NOTHING — no proxy involved
}
```

Silent, and untestable by inspection. Mitigation: the transactional boundary lives
in `LedgerService`, always invoked **across** beans, never internally.

### 10.3 Swallowing an exception poisons the transaction

Catching an exception inside a transactional method without rethrowing leaves the
transaction marked rollback-only; the commit then fails with
`UnexpectedRollbackException` far from the real cause. Rule: never catch a
persistence exception inside the boundary except the one deliberate case in §12,
which is handled by the caller *outside* it.

### 10.4 Some things cannot be rolled back

An SMS is not transactional. Sending a notification inside the boundary means a
rolled-back transfer still texts the customer — and a slow external call holds a
wallet lock while it runs. External effects go through a **transactional outbox**
committed with the transaction and dispatched after. Out of scope here, named so
the boundary is explicit.

### 10.5 Propagation of the failure record

Recording a declined transfer needs `REQUIRES_NEW`, because it must survive the
rollback of the transfer that failed. That inner transaction must not touch the
locked wallet rows, or it will deadlock against its own parent.

### 10.6 Proving it

`TransactionRollbackTest` injects a failure into the credit leg after the debit
has been applied, then asserts: source balance unchanged, destination balance
unchanged, **zero** ledger entries written, and no `SUCCESS` transaction row. That
test is the direct answer to this requirement.

---

## 11. Concurrency

### Pessimistic locking, not optimistic

`SELECT ... FOR UPDATE` on the wallet rows via `@Lock(PESSIMISTIC_WRITE)`.

Optimistic locking is the wrong default here. A hot wallet — a merchant taking
hundreds of payments a minute — produces a retry storm where most attempts do work
and discard it. Pessimistic locking gives a bounded wait instead of unbounded
retries. `@Version` stays on the row as a second net against any write that
bypasses the intended path.

### Deterministic lock ordering

The part that is easy to miss and deadlocks at 2am. Two simultaneous transfers,
`A → B` and `B → A`:

```
without ordering:  T1 locks A, waits for B
                   T2 locks B, waits for A      -> deadlock

with ordering:     both sort by account id, both lock A then B
                   -> one proceeds, one waits, neither deadlocks
```

Locks are always acquired **sorted by account id ascending**, never in request
order. Lock timeout is bounded (5s) so a pathological case fails loudly instead of
hanging a connection — see the pool interaction in §14.

### Proving it

`ConcurrentTransferTest`: two accounts seeded ₦100,000 each; 200 threads, half
A→B and half B→A, randomised amounts, released simultaneously by a
`CountDownLatch`. Assertions: total money **exactly** ₦200,000; no negative
`balance_after`; ledger sums to zero; successful transaction count equals ledger
pair count. It fails against the naive implementation and passes against the
locked one — that contrast is the point. It runs against real PostgreSQL (§7).

---

## 12. Idempotency and in-doubt transactions

The important case is not the duplicate click. It is: **the client timed out and
does not know whether the money moved.**

### The key design property

The receipt row and the money movement **commit in the same database
transaction** (§9, step 12). Therefore:

> If no transaction record exists for an idempotency key, the transfer
> definitively did not happen, and it is safe to retry.

That is a far stronger guarantee than any retry heuristic, and it makes the client
contract trivially simple.

The rejected alternative was reserve-then-execute: insert a `PENDING` row in its
own transaction, do the work, then update the status. It leaves rows stuck
`PENDING` whenever a process dies mid-flight — permanently poisoning that
idempotency key and requiring a reaper job to resolve in-doubt records. Since the
single-transaction design has no in-doubt window at all, the machinery is
unnecessary.

### Resolving an unknown outcome

The client generates the key, so the client can always ask:

```
GET /api/v1/transfers/idempotency/{key}
  200 -> here is the outcome (SUCCESS or FAILED). Do not retry.
  404 -> it did not happen. Safe to retry with the same key.
```

### Concurrent duplicates

Two identical requests genuinely in flight at once: both attempt to insert the
same idempotency key, the unique index serialises them, one commits, the other
receives a constraint violation, catches it, re-reads by key, and returns the
committed receipt. Correct under either interleaving, with the **database** as the
arbiter rather than an application-level check that would itself race.

### Same key, different payload

If a client reuses a key with a different amount, returning the original silently
would mask a real second payment. The request is fingerprinted (SHA-256 of the
canonical body, stored as `request_hash`); a mismatch is
`422 IDEMPOTENCY_KEY_REUSE`.

### Declines are idempotent too

A transfer declined for insufficient funds is recorded (§10.5) with its key. A
retry of that key returns **the decline**, not a fresh attempt — same key, same
answer, always. A genuinely new attempt requires a new key. This is stated in the
API docs because it will surprise someone otherwise.

### Retention

Keys are kept 90 days, then archived. Keeping them forever grows a random-UUID
unique index without bound (§8); expiring them means a retry older than the
window would be reprocessed. Stated as a known, bounded risk.

---

## 13. Caching

Governing principle:

> **A cache may only make the system fail faster. It may never make it succeed
> wrongly.**

| Data | Cached? | Reasoning |
|---|---|---|
| Account metadata — number → id, owner, currency, status | **Yes**, 5 min TTL | Two lookups on every transfer; changes rarely. The biggest win available |
| User profile reads | **Yes**, short TTL | Read-heavy, non-authoritative |
| *Display* balance — what an app screen shows | **Only with an explicit TTL and labelled indicative** | Users tolerate a few seconds of staleness on a screen |
| **Authorization balance — the number that decides whether a debit proceeds** | **Never** | Always primary, always under lock, always inside the transaction |
| Idempotency lookups | **No** | A stale miss means a double charge. The unique index is the only authority |

The distinction between **display balance** and **authorization balance** is the
one that matters. Conflating them is how double-spends are built.

**Why a cached account status is still safe:** a cached `ACTIVE` on a
just-frozen account could let a request through the fast path — so status is
**re-read under lock inside the transaction** anyway (§9, step 7). The cache can
therefore only reject earlier, never approve wrongly. That is the principle above,
applied concretely.

Implementation: Spring's cache abstraction backed by **Redis**, shared across every
instance. An in-process cache was rejected for this scale — it is per-instance, so
N instances mean N cold caches, N times the database load after a deploy, and
instances that disagree with one another. The cost is a network hop per lookup,
which is the right trade for reads at this volume.

Three details that matter once traffic is real:

- **A cache outage must not be a service outage.** A `CacheErrorHandler` degrades
  every failure to a logged warning and a database read. Spring's default is to
  propagate, which would let a Redis restart take the wallet down with it.
- **Cache stampede.** `@Cacheable(sync = true)` collapses concurrent misses on one
  key, so a cold hot key costs one query rather than hundreds. Across instances
  this bounds the stampede to one query per instance; a distributed lock would be
  the next step if that ever proved insufficient.
- **Deserialisation safety.** Values are stored as JSON with a polymorphic type
  allow-list limited to this application's packages. Unrestricted default typing
  is a known remote-code-execution vector if the cache store is ever compromised.

Nothing a correctness decision depends on is cached.

---

## 14. Scale, tradeoffs and failure modes

### Assumed operating envelope

Anchored to a mid-size Nigerian wallet business:

| | Assumed |
|---|---|
| Registered wallets | 1M (100K in year one) |
| Daily active | ~200K |
| Transactions/day | ~500K |
| Average throughput | ~6 TPS |
| **Peak throughput** | **~100 TPS** — month-end salary runs, Fridays, December; 10–20× average is normal here |
| Design headroom | 500 TPS sustained |
| Read:write ratio | ~10:1 |

The number that drives the architecture is **row growth**: every transfer writes
three rows, so 500K/day is **1.5M rows/day, ~550M rows/year, ~80GB/year with
indexes**, in an append-only table. Hence keyset pagination (§8) and monthly range
partitioning of `ledger_entries` once it passes ~100M rows, with cold partitions
detached to archive storage.

### Tradeoffs accepted knowingly

| Decision | Bought | Paid |
|---|---|---|
| Cached balance instead of summing the ledger | O(1) reads | Denormalisation that *can* drift — paid for with an invariant test and a reconciliation job. Escalation: periodic balance snapshots, so `balance = snapshot + entries since` |
| Pessimistic locking | Bounded waits, no retry storms | Serialised writes per wallet (~500 TPS per row); fine for consumer accounts |
| `NUMERIC(19,4)` over `BIGINT` minor units | JPA ergonomics, readability | Slightly larger and slower than integer kobo. Both are exact — this is not the float mistake |
| `READ COMMITTED` + explicit locks over `SERIALIZABLE` | Throughput, no serialisation-failure retries | Requires discipline — mitigated by only one class being allowed to lock |
| 90-day idempotency retention | Bounded index growth | A retry older than the window would reprocess |

### The bottleneck this design introduces

**The house account is a hot row.** Every funding operation locks the *same*
`wallet_balances` row, making it the system's throughput ceiling — a bottleneck
created by the double-entry design itself. Fix: shard it into N sub-accounts
selected by hash, summed for reporting. The same shape of problem applies to any
high-volume merchant account.

### The failure mode that causes outages

**Lock waits consume the connection pool.** Ten connections blocked on one
contended row is a total service outage, not a slow endpoint. Therefore **lock
timeout must be strictly less than connection-acquisition timeout**, so the wallet
endpoint fails loudly rather than taking the service down with it.

### Race conditions — full inventory

| # | Race | Mitigation |
|---|---|---|
| 1 | Lost update — two debits read-modify-write | `SELECT FOR UPDATE` |
| 2 | Deadlock on simultaneous A→B / B→A | Ascending account-id lock ordering |
| 3 | Double-spend via concurrent retry | Unique index on idempotency key, catch the violation |
| 4 | Duplicate registration — two signups, same email | DB unique index, **not** `existsByEmail` then insert |
| 5 | Account number collision | Sequence-derived, not random-and-check |
| 6 | Balance checked in one transaction, debited in another | Check *inside* the locked transaction |
| 7 | TOCTOU — account frozen mid-transfer | Re-read status under lock |
| 8 | Wallet provisioning half-completes | Same transaction + unique FK |
| 9 | Read-your-own-write — transfer, then a stale balance from a replica | Statements may use replicas; balances and pre-debit checks never do |

4, 5, 7 and 9 are the ones usually missed. 9 especially: it is tempting to push
all reads to a replica, but a balance that decides whether money moves can never
come from a lagging one.

### Refused at this scale

No sharding, no CQRS, no event sourcing, no service split — and specifically,
**no Redis-cached balance** behind a debit decision (§13).

---

## 15. API surface

All responses use the `ApiResponse<T>` envelope.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/users` | Register user, auto-provision account + wallet |
| GET | `/api/v1/users/{id}` | User with accounts |
| GET | `/api/v1/accounts/{accountNumber}` | Account detail |
| GET | `/api/v1/accounts/{accountNumber}/balance` | Current balance |
| GET | `/api/v1/accounts/{accountNumber}/statement` | Paged ledger history (keyset) |
| POST | `/api/v1/accounts/{accountNumber}/fund` | Simulated inbound credit |
| POST | `/api/v1/transfers` | **Transfer between accounts** |
| GET | `/api/v1/transfers/{reference}` | Transaction status by reference |
| GET | `/api/v1/transfers/idempotency/{key}` | **In-doubt resolution** (§12) |

Plus `/swagger-ui.html` and `/actuator/health`.

### Error model

```json
{
  "success": false,
  "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient funds in account 1000000015",
  "timestamp": "2026-07-21T14:32:10.221Z",
  "correlationId": "b7f3a1c2"
}
```

| Code | HTTP | Trigger |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean validation failure, with per-field detail |
| `ACCOUNT_NOT_FOUND` | 404 | Unknown account number |
| `INSUFFICIENT_FUNDS` | 422 | Source balance below amount |
| `ACCOUNT_NOT_ACTIVE` | 422 | Frozen or closed account |
| `SAME_ACCOUNT_TRANSFER` | 422 | Source equals destination |
| `CURRENCY_MISMATCH` | 422 | Accounts differ in currency |
| `INTER_BANK_NOT_SUPPORTED` | 422 | `destinationBankCode` is not ours — refused explicitly, never treated as internal (§6) |
| `IDEMPOTENCY_KEY_REUSE` | 422 | Same key, different payload (§12) |
| `TRANSFER_LIMIT_EXCEEDED` | 422 | Above configured maximum |
| `DUPLICATE_USER` | 409 | Email or phone already registered |
| `INTERNAL_ERROR` | 500 | Unhandled — logged with correlation id, never leaked |

Declined transfers are persisted as `FAILED` (§10.5). A system that only records
successes is blind to the half of the traffic that matters most during an incident.

---

## 16. Testing strategy

The whole suite runs against a real PostgreSQL database — `wallet_test` by
default, overridable with `TEST_DB_URL`. CI runs the same command against a
PostgreSQL service container.

| Test | Proves |
|---|---|
| `NubanGeneratorTest` | Check digit against the published GTBank vector (§6); rejection of the wrong 13-digit variant; sequential uniqueness; validation of a mistyped number |
| `AccountResolutionTest` | Same NUBAN under two institution codes resolves to two distinct accounts; a foreign bank code is refused, not silently treated as internal |
| `TransferServiceTest` | Rules in isolation: insufficient funds, same account, inactive, bad scale, over limit |
| `TransactionProcessorFactoryTest` | Correct processor resolved per type; unknown type rejected |
| `TransferIntegrationTest` | Full HTTP path: happy path, 404, 422, validation shape |
| **`TransactionRollbackTest`** | **Debit reverts entirely when the credit leg fails** (§10.6) |
| `IdempotencyTest` | Replay returns the original receipt; no double debit; key reuse with a changed payload rejected |
| **`ConcurrentTransferTest`** | **No lost updates, no deadlock, money conserved to the kobo** |
| `LedgerInvariantTest` | Ledger sums to zero; per-account sum equals cached balance |

The rollback, idempotency and concurrency tests are the ones worth reading first,
and the README says so.

---

## 17. Delivery plan

Deadline COB Thursday 23 July 2026; today is Tuesday 21 July.

| Phase | Work |
|---|---|
| 1 | Build config, Flyway migrations, profiles, Docker, CI |
| 2 | Entities, enums, repositories with the locking query and indexes |
| 3 | DTOs, `ApiResponse`, exception hierarchy, `@RestControllerAdvice`, correlation-id filter |
| 4 | `NubanGenerator`, `UserAccountService`, `LedgerService`, processors + factory, `TransactionService` |
| 5 | Controllers, OpenAPI annotations, caching, demo seeder |
| 6 | Test suite — rollback, idempotency and concurrency last |
| 7 | `mvnw verify` green end to end |
| 8 | README, ERD, curl walkthrough, git history |

Buffer is deliberately held on Thursday. A submission that lands broken on
deadline day is worse than one that landed narrower on Wednesday. Commits stay
small and conventional — the git history is part of what is assessed.

---

## 18. Assumptions

1. **Single currency (NGN).** Currency is modelled and mismatches rejected; FX is
   out of scope.
2. **One account per user at registration**, but the schema is one-to-many.
3. **Funding is simulated** — no real PSP. Modelled honestly as a double-entry
   movement from a house account rather than faked by incrementing a balance.
4. **"Intra transfer" means wallet-to-wallet within our own institution code.**
   A transfer naming a different bank code is rejected explicitly rather than
   assumed internal; inter-bank settlement (NIP) is out of scope.
5. **Account numbers are unique per institution, not globally** — per the CBN
   standard (§6). The schema reflects this from day one even though only one
   institution exists today, because retrofitting a composite key onto a live
   ledger is a migration nobody wants to run.
6. **No overdrafts** for customer accounts. Only the `SYSTEM` account may go
   negative, which is what a liability account is for.
7. **Amounts accepted to 2 decimal places**; greater precision is rejected.
8. **No authentication**, per agreed scope — flagged as production gap #1.
9. **PostgreSQL is the only database**, in development, test and production. No
   in-memory substitute is used anywhere, so the tests prove the real locking
   behaviour rather than an approximation of it.

---

## 19. Open questions

Would be raised with the hiring contact if this were real work.

1. Should a wallet ever go negative under an approved overdraft product?
   Assumed **no**.
2. Is a transaction PIN expected? Assumed **no** — it belongs with the
   authentication story, which is out of scope.
3. Should statements be exportable (CSV/PDF)? Assumed **no**; JSON pagination only.
