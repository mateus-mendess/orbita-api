---
name: spring-data-mongodb
description: Guidelines for MongoDB persistence using Spring Data MongoDB in Spring Boot. Use when creating or modifying repositories, document models, indexes, queries, or MongoDB configuration.
---

# Spring Data MongoDB Skill

Follow `AGENTS.md` (Section 5) for per-user data scoping (`userId` from authenticated principal), pagination, and environment-based configuration.

## Document Modeling

- **Explicit collection mapping:** Annotate persistence models in `internal/model/` with `@Document(collection = "<plural_name>")` (e.g., `users`, `tasks`, `habits`, `notes`) and `@Id` on the `String id` field.
- **Lombok compatibility:** Use `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, and `@Builder` explicitly. Avoid `@Data` on document models.
- **Timestamps & Dates:** Use `Instant` (UTC) for `createdAt` and `updatedAt`. Follow `docs/data-model.md` strictly for plain string date/time fields (`YYYY-MM-DD`, `HH:mm`).
- **Indexes:** Declare uniqueness with `@Indexed(unique = true)` (e.g., `email` on `User`) and query-critical composite indexes with `@CompoundIndex` (e.g., `{ userId: 1, createdAt: -1 }`). Enable `spring.data.mongodb.auto-index-creation=true` in `application.properties` so indexes exist in dev and Testcontainers.
- **Boundary isolation:** Never expose `@Document` models in controller responses or cross-module `api/` packages; map to/from DTOs via MapStruct.

## Repositories & Queries

- **Prefer `MongoRepository<Model, String>`:** Use Spring Data derived query methods (`findByEmail`, `findByIdAndUserId`, `findAllByUserId`, `deleteByIdAndUserId`) to keep repositories declarative and minimal.
- **Enforce tenant scoping at the query level:** For all user-owned collections (`tasks`, `habits`, `notes`), store `userId` as an indexed field and always include `userId` in lookup, list, and delete queries so one user can never access another user's document.
- **Pagination:** Always bound list queries using Spring Data `Pageable` (`Page<T>` or `Slice<T>`). Never expose unbounded `findAll()` endpoints.
- **When to use `@Query` or `MongoTemplate`:** Use `@Query` only when a derived method name becomes hard to read. Use `MongoTemplate` only for targeted partial updates or aggregations that `MongoRepository` cannot express cleanly.

## Business Rules & Duplicate Keys

- Keep business logic (such as handling an existing unverified user vs. rejecting a verified one) in the `Service`, not inside a custom repository.
- Translate `DuplicateKeyException` into `OrbitaApiException` (HTTP `409 Conflict` with the contract's `ErrorCode`) when concurrent writes hit a unique index.

## Configuration

- Configure MongoDB strictly via `spring.data.mongodb.uri=${MONGODB_URI}` (never hardcode credentials or hosts).
- Rely on Spring Boot auto-configuration; do not manually instantiate `MongoClient` unless explicitly required.