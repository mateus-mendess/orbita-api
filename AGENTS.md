AGENTS.md — Orbita API

Guidance for AI coding agents working on this repository. Read it fully before changing code.

1. Overview

Orbita is a routine-management app. This repository is its backend API (the front-end is a separate Expo / React Native repo).

Feature modules: task, habit, note (independent from each other), plus auth (user account, own registration, login, e-mail verification codes and password recovery).

College project built with AI-assisted programming. Favor simple, readable, minimal code.

Before implementing models or endpoints, read docs/data-model.md and docs/endpoints.md. They are the contract with the front-end; do not deviate from them without asking.

2. Non-negotiable rules

These override everything else, including speed.

Never invent anything. If you lack information, an explanation or a decision, stop and tell me: what you cannot do yet, and exactly what I must provide or decide. Do not guess, do not fill gaps with assumptions, and do not create fake, hard-coded, placeholder or stub logic just to deliver something or to make tests pass. Everything delivered must be real.
Do only what I ask. If I ask for user registration, implement only user registration. Do not refactor, rename, "improve" or update anything else. If you notice something else worth changing, mention it at the end and leave it alone.
Never add a dependency without asking first. Every time, no exceptions: this includes the libraries listed in section 3 and test libraries. Ask with the name, the version and the reason, and wait for my answer before touching pom.xml.
A task is done only when its tests exist, were run, and pass (section 9). Never use workarounds to make tests pass: do not weaken, skip, disable or delete tests, do not special-case test inputs, and do not change production code just to satisfy a test. If a test fails because of a real problem you cannot solve, stop and report it (rule 1).
Ask before changing the architecture, the data model or the auth flow.
3. Stack (target versions)
   Java 25 (LTS), Spring Boot 4.1.3, Maven
   Spring Modulith (module structure and verification)
   Spring Security + JWT signed with RSA
   Cloud Firestore via Firebase Admin SDK (no JPA, no Flyway)
   Redis (Spring Data Redis) for verification codes and short-lived data
   Spring Mail (SMTP), Lombok, MapStruct, Bean Validation
   Tests: JUnit 5, Mockito, MockMvc, Testcontainers (Firestore emulator and Redis)
   API docs: springdoc-openapi (Swagger)

This is the planned stack. Rule 2.3 still applies: ask before adding each dependency to the build.

4. Architecture

System: modular monolith with Spring Modulith. Each feature is a top-level package under the base package and is one module. Modules must not depend on each other's internal package.

Code: Spring MVC layers inside each module: Controller → Service → Repository → Firestore.

com.orbita.api
├── OrbitaApiApplication.java
├── common/                  shared and generic only (OPEN module)
│   ├── exception/           ErrorCode, AppException, GlobalExceptionHandler
│   ├── security/
│   └── config/              Firebase, Redis, Mail
├── auth/                    account + authentication (single module)
├── task/
├── habit/
└── note/
├── api/                 what other modules may use (interfaces, DTOs)
└── internal/            private to the module
├── controller/
├── service/
├── repository/
├── model/
├── dto/
└── mapper/

Rules:

api/ is exposed as a named interface (@NamedInterface("api") in its package-info.java). Nothing in internal/ may be accessed from outside the module.
task, habit and note must not reference each other. If a cross-module need appears, ask first; if approved, expose it through the owner's api/.
Controllers: HTTP only. Services: business rules. Repositories: the only code that touches Firestore. Never expose Firestore models in responses; use DTOs.
Keep ApplicationModules.of(OrbitaApiApplication.class).verify() passing.
5. Data (Firestore)
   Data is per user: users/{userId}/tasks, users/{userId}/habits, users/{userId}/notes.
   userId always comes from the authenticated principal, never from the client.
   Paginate or limit list queries.
   No schema migrations. Composite indexes live in firestore.indexes.json; data migrations (if ever needed) are idempotent scripts in migrations/, tracked in a _migrations collection.
   Credentials and keys come from environment variables. Never commit secrets.
6. Auth and verification codes
   Passwords hashed with BCrypt. Login and e-mail verification return a JWT access token signed with an RSA private key (Base64 PEM via env vars). Access token only: no refresh token, no logout endpoint.
   A 6-digit e-mail code is required for registration and password recovery:
   generated with SecureRandom, stored hashed in Redis with TTL (10 min), key verification:{purpose}:{identifier} (see docs/data-model.md)
   single use, max 5 attempts, 60 s resend cooldown
   password recovery never reveals whether an e-mail exists
7. API and errors
   REST + JSON under /api/v1, plural resource names, correct HTTP status codes. Task/habit dates (YYYY-MM-DD) and times (HH:mm) are plain strings as sent by the client; only createdAt/updatedAt are ISO-8601 UTC.
   One GlobalExceptionHandler builds all error responses. The API returns error codes only, no messages; the front translates them.
   ErrorCode is an enum with no extra fields; the constant name is the code (e.g. VALIDATION_ERROR, RESOURCE_NOT_FOUND). Exceptions carry the code and the HTTP status. Validation annotations use the code as message.
8. Configuration

Three properties files, with no duplication between them:

application.properties: everything shared by development and production.

A property lives in exactly one file. Secrets are never written in any of them; use environment variables (${VAR}).

9. Testing and definition of done
   Unit tests: JUnit 5 + Mockito, for services and other business logic.
   Integration tests: MockMvc, with Testcontainers for the real infrastructure (Firestore emulator and Redis), so they resemble production.
   Cover success and failure paths for everything you implement.
   Before reporting a task as done: write the tests, run them (./mvnw verify), and make sure they all pass. Report the result honestly, including anything that could not be run.
   Details of how to write tests are in the testing skill.
10. Coding rules
    Minimum complexity. Simplest code that works. No speculative abstractions or generic base classes. If an implementation grows too large, stop and simplify.
    Constructor injection via Lombok @RequiredArgsConstructor; never field @Autowired. Lombok is welcome (@Getter, @Builder, etc.). Use records for DTOs.
    Validate all input at the controller boundary.
    Never log passwords, tokens or verification codes.
    Code, comments, logs and commit messages in English.
    When something is ambiguous, ask a short question (see rule 2.1).
11. Skills

Procedures such as testing, API documentation, Firestore data access, commits and the feature workflow live in separate skills. Use them instead of improvising. The "senior Java developer" role is defined separately and is not part of this file.
