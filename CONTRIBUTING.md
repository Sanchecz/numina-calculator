# Contributing

1. Keep production code offline and dependency-light.
2. Add tests for every parser, state or UI regression.
3. Run `.\scripts\verify.ps1 -Connected` before submitting a change.
4. Do not add lint baselines, ignored tests, swallowed exceptions or relaxed compiler flags to make CI green.
5. Never commit keystores, credentials, local SDK paths, user data or generated build directories.
6. Update architecture, privacy, security and store documentation when behavior changes.

Code should remain compatible with API 23 and compile/target API 37. Treat accessibility, localization and offline behavior as release requirements, not optional polish.
