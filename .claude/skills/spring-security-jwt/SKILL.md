---
name: Spring Security & JWT Auditor
description: Use this skill when auditing security configurations, filters, endpoints, or authentication mechanisms in the Spring Boot application.
---

# Instructions
When auditing security aspects:
- **Stateless Architecture:** Ensure the Security Filter Chain is configured as stateless (`SessionCreationPolicy.STATELESS`), as this API serves a mobile application.
- **JWT Handling:** Verify token extraction, signature validation, and secure context population.
- **Endpoint Protection:** Check if endpoints properly enforce role-based or authentication-based access control (`SecurityFilterChain` configuration). Look for accidental open endpoints or missing authorization annotations.
