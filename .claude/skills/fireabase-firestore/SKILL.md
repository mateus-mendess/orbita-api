---
name: firebase-firestore
description: Guidelines for Cloud Firestore persistence using Firebase Admin SDK in Spring Boot. Use when creating or modifying repositories, Firestore document models, queries, batches, transactions, or Firebase configuration.
---

# Firebase Firestore Skill

Follow `AGENTS.md` (Section 5) for collection paths (`users/{userId}/...`), principal-derived `userId`, and environment-based credentials.

## Document Models & Serialization

- **Never use Java `record` for Firestore documents:** The Firebase Admin SDK requires a public no-arg constructor and public getters/setters to deserialize documents.
- **Lombok compatibility:** Use `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, and `@Builder` explicitly on document POJOs. Avoid `@Data` and avoid `@Builder` without `@NoArgsConstructor`.
- **Field annotations:** Use `@DocumentId` for the document ID field, `@PropertyName` when a Firestore field name differs from Java naming, and `@Exclude` for transient helper fields/methods.
- **Boundary isolation:** Keep document POJOs strictly inside `internal/model/` and `internal/repository/`. Convert to/from DTOs before returning from services or controllers.

## Handling `ApiFuture<T>`

- Never leak `ApiFuture`, `ExecutionException`, or `InterruptedException` outside the repository layer.
- Centralize `ApiFuture.get()` unwrapping in a small helper method:
  - On `InterruptedException`, restore the interrupt flag (`Thread.currentThread().interrupt()`) and throw an unchecked infrastructure exception.
  - On `ExecutionException`, unwrap the cause and translate it cleanly rather than swallowing it.

## Queries, Pagination & Cost Control

- **Always bound list queries:** Apply `.limit(size)` on every collection query.
- **Cursor-based pagination only:** Use `.orderBy(...)` combined with `.startAfter(...)` (document snapshot or ordered cursor values). Never use `.offset()`, which still bills reads for skipped documents.
- **Prevent N+1 reads:** Never fetch documents inside a loop. Use `firestore.getAll(DocumentReference...)` for batch lookups by ID.
- **Respect Firestore hard limits:**
  - Max 30 values in `whereIn`, `whereNotIn`, or `whereArrayContainsAny`.
  - Max 500 operations per `WriteBatch`.
  - Composite queries (multiple fields / inequality + sort) must have matching index definitions in `firestore.indexes.json`.
- **Single-document lookups:** Return `Optional<Model>` from repositories when fetching by ID; check `snapshot.exists()` before calling `snapshot.toObject(...)`.

## Atomic Writes & Transactions

- Use `WriteBatch` for atomic multi-document writes that do not depend on prior reads.
- Use `firestore.runTransaction(...)` only when a read-modify-write must be atomic; keep transaction callbacks short and side-effect-free (transactions may retry).
- Follow `docs/data-model.md` strictly for timestamp representation (`createdAt`/`updatedAt` in ISO-8601 UTC; task/habit date/time fields as plain strings). Never mix formats.

## Spring Configuration

- Register `Firestore` as a Spring `@Bean` in `common/config/` and inject it into repositories via constructor injection.
- Guard `FirebaseApp.initializeApp(...)` by checking `FirebaseApp.getApps().isEmpty()` so multiple test contexts do not fail on re-initialization.
- Never call `FirestoreClient.getFirestore()` directly inside repositories.