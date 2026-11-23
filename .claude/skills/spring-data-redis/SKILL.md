---
name: spring-data-redis
description: Guidelines for ephemeral state, verification codes, TTLs, and cooldowns using Spring Data Redis. Use when creating or modifying Redis repositories, stores, key structures, or Redis configuration.
---

# Spring Data Redis Skill

Follow `AGENTS.md` (Section 6) and `docs/data-model.md` for verification code rules (6-digit `SecureRandom`, 10-minute TTL, max 5 attempts, 60-second cooldown, and key naming).

## Role of Redis in the System

- **Ephemeral state only:** Use Redis strictly for short-lived, disposable data (verification codes, resend cooldowns, rate limits, or short-lived cache). Never store persistent domain data in Redis.
- **No immortal keys:** Every key written to Redis must have an explicit `Duration` TTL. Never create keys without expiration.
- **Fail closed:** If Redis is unavailable during an auth or verification flow, let the operation fail cleanly. Never bypass code verification or cooldown checks on Redis errors.

## Template & Serialization

- **Prefer `StringRedisTemplate`:** Use `StringRedisTemplate` for simple values, counters, hashes, and explicit JSON payloads (serialized via `ObjectMapper`).
- **Never use default JDK serialization:** Avoid `RedisTemplate<Object, Object>` with `JdkSerializationRedisSerializer` — it produces opaque binary keys/values and breaks across class refactors. If a typed `RedisTemplate` is ever needed, configure `StringRedisSerializer` for keys and JSON serialization for values in `common/config/`.

## Atomicity & Race Conditions

- **Always set value and TTL atomically:** Use `opsForValue().set(key, value, ttl)` (single `SET EX` command). Never call `set(...)` followed by `expire(...)`, which can leave orphan keys if the process crashes in between.
- **Atomic cooldowns:** Implement resend cooldowns using `opsForValue().setIfAbsent(cooldownKey, "1", Duration.ofSeconds(60))` (`SET NX EX`) so concurrent resend clicks cannot trigger duplicate e-mails.
- **Atomic attempt counting:** Increment failed attempts with `opsForValue().increment(attemptsKey)` (or `opsForHash().increment(...)`) rather than reading, incrementing in Java, and writing back. Ensure the TTL is preserved or set on creation.
- **Strict single-use consumption:** Delete the verification key immediately upon successful validation (or when max attempts are reached) so a code can never be replayed.

## Security & Code Verification

- **Constant-time comparison:** Compare hashed verification codes using `MessageDigest.isEqual(...)` (or the password encoder's `matches(...)` if using BCrypt) to prevent timing attacks. Never compare hashes with `String.equals(...)`.
- **Clean keys:** Never place raw verification codes, passwords, or JWTs inside Redis key names. Keep keys predictable and lowercase using colon-separated namespaces (e.g., `verification:{purpose}:{identifier}`).

## Layer Isolation & Configuration

- **Encapsulate Redis access:** Keep `StringRedisTemplate` strictly inside dedicated repository/store classes (e.g., `VerificationCodeRepository` under `auth/internal/repository/`). Services must interact with domain methods (`save`, `find`, `incrementAttempts`, `delete`), never raw Redis operations or key string concatenation.
- **Configuration:** Configure Redis exclusively through Spring Boot `spring.data.redis.*` properties backed by environment variables. Never hardcode hosts, ports, or passwords.
