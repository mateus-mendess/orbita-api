---
name: spring-modular-mvc
description: Architecture guidelines for Spring Modulith modular monolith boundaries and internal Spring MVC layering. Use when creating modules, controllers, services, validators, mappers, or cross-module contracts.
---

# Spring Modular MVC Skill

Follow `AGENTS.md` (Sections 4 and 7) for package layout, module list, `/api/v1` conventions, and `ErrorCode`-based error handling.

## Spring Modulith Boundaries

- **Explicit public API:** Every module that exposes functionality to other modules must place public interfaces and DTOs inside `<module>/api/` with a `package-info.java` annotated with `@NamedInterface("api")`.
- **Strict encapsulation:** Never import anything from another module's `internal/` package. Keep internal classes package-private when they do not need to be accessed across subpackages.
- **Cross-module communication:** Only add cross-module dependencies after user approval. Prefer calling an explicit interface in the owner's `api/` package, or publishing a Spring `ApplicationEvent` when decoupled async/side-effect handling makes sense. Never inject another module's repository.
- **Common module discipline:** Keep `common/` strictly for generic, domain-agnostic infrastructure (`exception`, `security`, `config`). Never put feature-specific models or business rules in `common/`.

## Internal Spring MVC Layers

Keep the flow unidirectional: `Controller → Service → Repository`.

- **Controller (`internal/controller/`):**
  - Handles HTTP routing, `@Valid` request binding, extracting `userId` from the authenticated principal, delegating to a single service method, and returning `ResponseEntity` with the proper status (`200 OK`, `201 Created`, `204 No Content`).
  - Contains zero business logic and zero `try/catch` blocks (let `GlobalExceptionHandler` handle errors).
- **Service (`internal/service/`):**
  - One cohesive `<Resource>Service` per resource covering standard CRUD and domain operations (do not split into one service class per action).
  - Enforces business rules, orchestrates repository/validator calls, and throws `OrbitaApiException` with the appropriate `ErrorCode` and HTTP status.
  - Must not depend on HTTP/servlet types (`ResponseEntity`, `HttpServletRequest`).
- **Validator (`internal/service/` or `internal/validator/`):**
  - Extract a focused `<Resource>Validator` component when business validation rules (ownership, state transitions, cross-field rules) would clutter the service.
- **Mapper (`internal/mapper/`):**
  - Use **MapStruct** (`@Mapper(componentModel = "spring")`) for compile-time DTO-to-model and model-to-DTO conversions (no reflection).
  - Keep mappers focused on structural field mapping; never inject repositories or put business logic inside mappers.
  - Handle ignored/system-managed fields (e.g., `id`, `createdAt`, `updatedAt`) explicitly with `@Mapping(target = "...", ignore = true)` instead of silencing warnings globally.
- **Repository (`internal/repository/`):**
  - Encapsulates all persistence access. Always receives `userId` explicitly as a parameter to enforce per-user data scoping.

## Validation & Error Handling

- Validate all incoming payloads on Request DTO `record`s using Jakarta Bean Validation annotations, setting `message` to the corresponding `ErrorCode` constant name.
- Throw `OrbitaApiException` for expected domain errors (not found, conflict, forbidden, invalid state).
- Never expose stack traces, internal exception messages, or Firestore details in API responses.
