# Agent coordination and open PR map

Last checked: **2026-10-18**. The canonical upstream base `master` is still the original setup baseline, commit `db893dcdb14fdd85a05437ad458dc70ef704ca2e`. Do not assume current work has been merged. Check PR status/head commits afresh before acting.

## Who does what

**Owner:** approves new work scopes, installs, network listeners, service changes and merges.

**Codex (Silent, Windows 11):** primary runtime implementer. Bounded Stage B assignment: original Forge two-player Lightning Bolt/Mountain AI JVM comparator, constructor/dependency inventory and **unchanged** TeaVM compilation attempt. Three fresh positive fixtures and an empty-hand negative. Must preserve original AI `FutureTask` / `Thread` / timed-get, genuine payment/cast/resolution; if a compatibility adapter is required, stop and request separate approval.

**Muse/Marvin:** independent asset-scanner contributor. Its [PR #10](https://github.com/rwZephyr/forge-adventure-web/pull/10) contains stdlib scanner + unit tests + 9-file cave manifest. Follow-up review flags one path-safety hole: `--plane` or `--common` pointing outside `--root` may allow the probe to hash an external atlas before rejecting a child asset. Finish containment test and fix or document a deliberately different policy before relying on root containment. Don't implement Codex's Stage B.

**Claude (new account):** independent read-only Stage B review, not another broad repo audit or competing implementation. See onboarding prompt from owner; start with PR #13 and pinned Java source paths. Review TeaVM concurrency classlib and real call graph, identifying evidence, blockers, and minimal authentic compatibility approach.

**ChatGPT:** integrator, evidence assessment, team coordination, documentation, preparing reviewable PRs. The owner retains all approval/merge authority.

## PR branches and dependencies

| PR | Purpose | State and dependency |
| --- | --- | --- |
| [#3](https://github.com/rwZephyr/forge-adventure-web/pull/3) | Existing portrait/touch UX research | Draft, on master |
| [#4](https://github.com/rwZephyr/forge-adventure-web/pull/4) | Forge/TeaVM feasibility source audit | Draft, on master |
| [#5](https://github.com/rwZephyr/forge-adventure-web/pull/5) | Muse saves/hosting research | Draft, on master |
| [#6](https://github.com/rwZephyr/forge-adventure-web/pull/6) | Integration gates / risk sequencing | Draft, on master |
| [#7](https://github.com/rwZephyr/forge-adventure-web/pull/7) | Muse asset licensing/distribution research | Draft, on master |
| [#8](https://github.com/rwZephyr/forge-adventure-web/pull/8) | Original SpriteGroup in Chromium / original asset | Draft, on master |
| [#9](https://github.com/rwZephyr/forge-adventure-web/pull/9) | Gate 2 A/B experimental design | Draft, on master |
| [#10](https://github.com/rwZephyr/forge-adventure-web/pull/10) | Muse's Adventure asset scanner | Draft, on master |
| [#11](https://github.com/rwZephyr/forge-adventure-web/pull/11) | Factory Stage A implementation and records | Draft, on master |
| [#12](https://github.com/rwZephyr/forge-adventure-web/pull/12) | Actual iPhone Safari evidence | Draft, **based on #11 head** |
| [#13](https://github.com/rwZephyr/forge-adventure-web/pull/13) | Stage B JVM/TeaVM baseline scope | Draft, **based on #12 head** |

The #11 → #12 → #13 branch stack is intentional for review, but merges/rebases need deliberate owner review and conflict resolution. Status edits are duplicated across different branches; do not overwrite or cherry-pick them uncritically.

## Core technical boundaries

- Browser toolchain: installed JDK 17, approved portable Maven 3.9.16 / Gradle 9.6.1, libGDX 1.14.2, gdx-TeaVM 1.6.1, TeaVM 0.15.0. Isolate repository-local caches; no global installs or unapproved downloads.
- Stage A success required explicit constructor metadata for 149 TriggerType enum entries/137 distinct classes, two selected replacement products and five selected keyword types. Broader Game and logger reachability is **not proven**.
- Stage B high-risk original code: `forge-ai/src/main/java/forge/ai/AiController.java` around lines 1685–1782 (FutureTask, Thread, timed get); `forge-core/src/main/java/forge/util/AITimeoutTracker.java`; `forge-game/src/main/java/forge/game/ability/ApiType.java` around line 234 (eager spell effects). See PR #13 for pinned links, card resource hashes, negative controls and required evidence.
- Real iPhone Safari passed only bounded drawing/touch and factory probes. It was tested over a one-time private Tailscale-address-bound HTTP listener that was shut down; existing Tailscale Serve/firewall/services remained untouched. **Do not reuse that session's network permission.**

## Contributing / reporting

Use a separate branch and draft PR for each approved assignment. Never merge, edit someone else's branch, serve artifacts, change networking or install dependencies without explicit user approval. Keep hypotheses separate from actual compiler/browser results. Record exact commands, hashes, observed errors and limitations; attach source references. If running under a free plan or limited context, inspect the narrow sources/PRs relevant to your role rather than cloning or dumping entire Forge.
