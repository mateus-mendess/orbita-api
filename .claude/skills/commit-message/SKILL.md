---
name: commit-message
description: Write commit messages in English following Conventional Commits 1.0.0. Use when the user lists changes and wants commit messages only, never to run git.
---
Commit Message (Conventional Commits 1.0.0)
Based on https://www.conventionalcommits.org/en/v1.0.0/

Scope
Output only the message text. Never run git commands or edit files unless explicitly asked.
The user may write in Portuguese; messages are always in English.
Input
The user gives one change or a list (bullets, free text, diff). Each self-contained change becomes its own commit.

If one item mixes types (e.g. fix + refactor), split it into separate commits.
If several items are one logical change, merge them.
If something essential is unclear (type, breaking or not), make the most reasonable assumption and state it in one short line. Ask only if it would change the type or the breaking status.
Format
<type>[optional scope][!]: <description>

[optional body]

[optional footer(s)]
feat = new feature (MINOR). fix = bug fix (PATCH).
Other types: build, chore, ci, docs, style, refactor, perf, test, revert (footer Refs: <sha>).
Scope: a noun in parentheses for the codebase area, e.g. fix(parser):. Keep it consistent across commits; omit if not useful.
Description: short summary of what changed.
Footers: after one blank line, git-trailer style (Refs: #123, Reviewed-by: Z). Tokens use - instead of spaces. Don't invent references; add them only if the user gave them.
Breaking change: ! before the colon, a BREAKING CHANGE: <description> footer (uppercase), or both. Valid on any type (MAJOR).
Body: explain WHY, never WHAT
The description already says what changed. The body exists only to say why: the motivation, the problem avoided, or the design decision behind the change.

Never restate or paraphrase the description. If the body only lists what was done, delete it.
Write a body only when the reason is not obvious from the description and the user gave it (or it is clearly implied). Never invent a motivation.
Most small commits (docs, style, simple additions, renames) need no body. Output just the first line.
Keep it to one short paragraph, wrapped at about 72 characters. Start from the reason ("to avoid...", "because...", "so that..."), not from the action.
Bad (restates the what):

refactor(role): return Optional from findByName

Have RoleRepository.findByName return Optional<Role> and throw a
RoleNotFoundException in UserService when a role is missing.
Good (states the why):

refactor(role): return Optional from findByName

Fail fast with RoleNotFoundException when a role is missing, instead of
letting a null reach the user_roles insert and fail far from the cause.
Style (best practice, not in the spec)
Lowercase type and scope, imperative mood ("add", not "added"), lowercase description, no trailing period.
First line up to 72 characters.
Output
Each message in its own code block, numbered when there is more than one, in a sensible order (migrations, then domain/service code, then tests/docs). After the blocks, add at most one or two lines, only for assumptions or splits/merges. Don't recap the rules.

Example:

1.

feat(migration): seed initial roles
2.

fix(role): query roles by RoleType instead of String

The parameter type did not match the entity attribute, so the derived
query would fail at runtime even though it compiles.
3.

refactor(user)!: drop password argument from UserService.create

BREAKING CHANGE: callers must set the password after creation.
