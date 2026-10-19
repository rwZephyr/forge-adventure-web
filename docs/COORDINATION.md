# Agent coordination and open PR map

Last checked: **2026-10-18**. The canonical upstream base `master` is still the original setup baseline, commit `db893dcdb14fdd85a05437ad458dc70ef704ca2e`. Do not assume current work has been merged. Check PR status/head commits afresh before acting.

## Who does what

**Owner:** approves new work scopes, installs, network listeners, service changes and merges.

**Codex (Silent, Windows 11):** primary runtime implementer. Completed Stage B approved baseline in [PR #15](https://github.com/rwZephyr/forge-adventure-web/pull/15): 3/3 genuine original Forge JVM AI Bolt decisions/casts passed plus empty-hand no-spell control. Actual original `FutureTask` / named eval thread / timed get witnessed by shared-memory JDI. TeaVM compilation failed with 93 severe diagnostics; stopped without adapting engines or libraries. Next implementation or canary requires separate owner scope approval.

**Muse/Marvin:** independent asset-scanner contributor. Its [PR #10](https://github.com/rwZephyr/forge-adventure-web/pull/10) contains stdlib scanner + unit tests + 9-file cave manifest. Final v1.1.1 resolved the previously flagged external `--plane`/`--common` path-containment issue; 21/21 unit tests passed independently, and Muse reports the unchanged nine-file cave inventory. Muse separately reviewed PR #15's TeaVM diagnostics, finding roughly 13 statically reachable root-cause groups after filtering runtime-dead log paths. Assessment is a PR #15 comment; no second compilation and no authorized fix.

**Claude (new account):** Independent read-only concurrency review preserved in [draft PR #16](https://github.com/rwZephyr/forge-adventure-web/pull/16). Its minimal Java classlib compatibility assessment predated PR #15; missing concurrency components are now observed compile failures but remain unimplemented.

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
| [#15](https://github.com/rwZephyr/forge-adventure-web/pull/15) | Stage B actual JVM AI and TeaVM compile diagnostics | Draft, **based on #13 head** |
| [#16](https://github.com/rwZephyr/forge-adventure-web/pull/16) | Claude's independent read-only concurrency review | Draft, on master |

The #11 → #12 → #13 → #15 branch stack is intentional for review, but merges/rebases need deliberate owner review and conflict resolution. Status edits are duplicated across different branches; do not overwrite or cherry-pick them uncritically.

## Core technical boundaries

- Browser toolchain: installed JDK 17, approved portable Maven 3.9.16 / Gradle 9.6.1, libGDX 1.14.2, gdx-TeaVM 1.6.1, TeaVM 0.15.0. Isolate repository-local caches; no global installs or unapproved downloads.
- Stage A success required explicit constructor metadata for 149 TriggerType enum entries/137 distinct classes, two selected replacement products and five selected keyword types. Broader Game and logger reachability is **not proven**.
- Stage B high-risk original code: `forge-ai/src/main/java/forge/ai/AiController.java` around lines 1685–1782 (FutureTask, Thread, timed get); `forge-core/src/main/java/forge/util/AITimeoutTracker.java`; `forge-game/src/main/java/forge/game/ability/ApiType.java` around line 234 (eager spell effects). See PR #13 for pinned links, card resource hashes, negative controls and required evidence.
- Real iPhone Safari passed only bounded drawing/touch and factory probes. It was tested over a one-time private Tailscale-address-bound HTTP listener that was shut down; existing Tailscale Serve/firewall/services remained untouched. **Do not reuse that session's network permission.**

## Contributing / reporting

Use a separate branch and draft PR for each approved assignment. Never merge, edit someone else's branch, serve artifacts, change networking or install dependencies without explicit user approval. Keep hypotheses separate from actual compiler/browser results. Record exact commands, hashes, observed errors and limitations; attach source references. If running under a free plan or limited context, inspect the narrow sources/PRs relevant to your role rather than cloning or dumping entire Forge.

## Architecture decision pending (after PR #15)

The genuine, unchanged JVM rules/AI path works. Pinned TeaVM 0.15.0 does **not** compile that path, with 93 recorded severe diagnostics across resource, threading, logging/event, reflection and supporting-library APIs. Claude suggests a narrow concurrency seam merits exploration, while Muse's independent root-cause analysis argues that solving concurrency alone leaves too many other statically reachable gaps. Neither opinion is a second compiler/browser experiment, and static reachability is not proof a method runs in the fixture. The owner has not approved broad compatibility work or a browser/JVM architectural pivot. Consider a small source-based design review of the **real Forge Adventure-to-duel** and **game-state/UI interaction** seams before attempting any browser/JVM RPC proof, with strict separation from remote desktop streaming. Preserve the original Java engine, browser-native portrait UI, and isolated player sessions.
