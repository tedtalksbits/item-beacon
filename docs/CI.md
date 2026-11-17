# CI

GitHub Actions, `.github/workflows/ci.yml`. Runs on every push to `main`, on pull requests, and by hand (Actions tab >
CI > Run workflow). Pushes that only touch Markdown, `docs/` or `run/` are skipped. Nothing is deployed anywhere.

| Job | Does | Fails when |
|---|---|---|
| Plugin build and unit tests | `./gradlew build` at the root: plugin, self-test plugin, MockBukkit unit tests | Compile error or a unit test fails |
| Client build and unit tests | `fabric-client/gradlew build`: the Fabric mod and its unit tests, then `ci/mixin-audit/run.sh` (loads the mod's classes in a real Fabric Loader, applies every mixin, checks the model wrapper and the screen's overrides, no game window) | Compile error (usually a Minecraft or Fabric class moved), a test fails, or the audit doesn't print `AUDIT RESULT OK` |
| Self-test on a real Paper server | Downloads the pinned Paper build, checks its SHA-256, runs `ci/run-selftest.sh` | The report doesn't end `SELFTEST PASSED`, or the server hangs (10 minutes) |
| Release (tags `v*` only) | All of the above, then publishes both jars (see Releases) | Any CI job fails, or the tag doesn't match the plugin version |

Each run keeps the jars as artifacts (`beacon-plugin`, `paper-selftest`, `item-beacon-client`) plus `selftest-report` (report and server
log). The self-test report is also printed on the run's summary page.

## Pins

The workflow's `env` block: `JAVA_VERSION`, `PAPER_VERSION`, `PAPER_BUILD`, `PAPER_SHA256` (the same Paper 26.3-211
as Glowing Glass). To move to a new Paper build, take the hash of the jar you tested locally
(`certutil -hashfile paper-<v>-<b>.jar SHA256` on Windows, `sha256sum` elsewhere) and change all three together.

## Running the self-test by hand

On Linux or in Git Bash, after `./gradlew build`:

```
ci/run-selftest.sh path/to/paper-26.3-211.jar /tmp/selftest-run
```

The second argument must be a folder that doesn't exist yet. Without internet access, add
`PAPER_SEED_DIR=path/to/an/existing/paper/server` so its `cache/`, `versions/` and `libraries/` are reused.
`SELFTEST_ONLY=beacon,gone` runs only the steps whose names start that way. About 30 seconds.

## Releases

`.github/workflows/release.yml`: the tag is the **plugin** version, and the client keeps its own version. Both jars go
in every release, and the notes name both versions.

1. Bump what changed: `version` in `build.gradle.kts` (plugin) and/or `version` in `fabric-client/gradle.properties`
   (client). A release always needs a new plugin version, since that's the tag (the first release, v0.1.0, is plugin
   0.1.0 with client 0.1.0).
2. Commit and push to `main`; wait for CI to go green.
3. Tag and push: `git tag v0.1.0` then `git push origin v0.1.0`.

The release workflow runs all of CI again, checks the tag matches the plugin version, then publishes
"Item Beacon v0.1.0" with `beacon-plugin-<plugin>.jar` and `item-beacon-client-<client>.jar`. If anything fails, nothing
is published; fix it, delete the tag (`git push origin :v0.1.0`, `git tag -d v0.1.0`) and tag again. The self-test
plugin is never released.

## Not yet

No player or game client in CI: opening containers, the menu, the screen, outlines and everything on screen stay manual
checks (`docs/TEST_PLAN.md`).
