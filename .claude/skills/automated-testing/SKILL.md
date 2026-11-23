---
name: automated-testing
description: Standards for unit, integration, and module verification tests using JUnit 5, Mockito, MockMvc, and Testcontainers (Firestore emulator and Redis). Use whenever writing, updating, or reviewing tests.
---

# Automated Testing Skill

Follow `AGENTS.md` (Sections 2.4 and 9) for the definition of done, test integrity rules, and `./mvnw verify` execution.

## Structure & Naming

- Follow **Arrange – Act – Assert** separated by blank lines. Keep each test focused on one behavior.
- Use `@DisplayName` with clear, behavior-oriented descriptions (e.g., `"Should reject task creation when title is blank"`).
- Use `@ParameterizedTest` (`@ValueSource`, `@CsvSource`, `@MethodSource`) for validation matrices and boundary inputs instead of duplicating near-identical test methods.
- Keep test setup local and readable; avoid heavy shared `@BeforeEach` fixtures that hide what a test actually depends on.

## Unit Tests (Services, Validators, Mappers)

- Use pure JUnit 5 with `@ExtendWith(MockitoExtension.class)`. Never boot the Spring context (`@SpringBootTest`) for unit tests.
- Mock only direct collaborators (e.g., `<Resource>Repository`, `<Resource>Validator`, `Clock`/ID helpers).
- **Never mock Firebase Admin SDK internals** (`Firestore`, `CollectionReference`, `DocumentReference`, `ApiFuture`). Chained SDK mocks are brittle and test nothing real; cover persistence in integration tests instead.
- Assert return values and thrown `OrbitaApiException` (`ErrorCode` and HTTP status). Use `Mockito.verify(...)` only for meaningful side effects (such as persistence calls or preventing writes on failure).

## Integration Tests (MockMvc + Testcontainers)

- Test end-to-end HTTP endpoints with `MockMvc` against real **Testcontainers** instances for the **Firestore Emulator** and **Redis**.
- Reuse shared container definitions (singleton container pattern or a shared test configuration) via `@DynamicPropertySource` so the test suite stays fast.
- **Ensure test isolation:** Use a unique `userId` per test (and clean up or flush Redis/emulator state when needed) so tests never depend on execution order.
- Verify the full contract:
  - HTTP status codes and JSON response structure matching `docs/endpoints.md`.
  - Error responses returning the exact `ErrorCode` with no extra fields.
  - Authentication and tenant isolation (e.g., unauthenticated requests rejected; User A cannot read, update, or delete User B's resources).

## Spring Modulith Verification

- Maintain a dedicated fast test verifying module boundaries:
  - `ApplicationModules.of(OrbitaApiApplication.class).verify();`
- Run it as part of the standard test suite so boundary violations fail `./mvnw verify` immediately.

## Minimum Coverage Checklist per Feature

Before finishing a task, ensure tests cover:
1. Happy path for every implemented operation.
2. Input validation failures at the controller boundary.
3. Domain/business rule failures (e.g., not found, invalid state, duplicate/conflict).
4. Security and ownership checks (missing/invalid token, accessing another user's resource).
