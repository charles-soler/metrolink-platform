### `CONTRIBUTING.md` (root)

```markdown
# Contributing

## Conventional Commits
Format: `<type>(<scope>): <subject>`
Types: feat, fix, docs, build, ci, test, refactor, perf, chore
Scopes: core, connector-sim, connector-bacnet, scheduler, historian, smoke-app, edge-service

Examples:
- feat(core): add in-memory historian and poll scheduler
- feat(connector-sim): implement SubscribePort
- docs(readme): add quickstart
- build(smoke-app): wire ServiceLoader and main

## Branches
- `main` — stable
- `feature/<short-name>` — development branches

## Build & Test
```bash
./gradlew build
