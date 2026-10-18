# Codex — Forge runtime and browser feasibility (research only)

## Mission
Determine whether **real upstream Forge Adventure** can run natively in browser via libGDX + TeaVM (including gdx-TeaVM), and design the smallest safe experiment to prove or falsify that route. Do **not** implement a whole port or create an unrelated blank libGDX demo.

## Read first
- Root `AGENTS.md`
- `docs/PROJECT.md`
- `docs/research/README.md`
- `docs/STATUS.md`

## Exclusive scope (Claude and Muse are studying other questions)
1. Identify Forge Adventure entry point(s), scene system, asset/runtime bootstrapping, Forge rules/AI dependency boundaries, build modules, JDK and libGDX version(s), Maven usage, and source references.
2. Establish a **reproducible baseline** build path using existing tools; execute only if safe for Silent. Record exact commands, versions and results. Do not alter system setup without consent.
3. Audit Java-to-browser blockers in the actual source/dependency graph: reflection, threads, native/desktop APIs, filesystem, serialization, fonts/assets, libraries, OpenGL and mobile Safari. Classify each as demonstrated blocker, plausible blocker, or unknown.
4. Contrast gdx-TeaVM and relevant GWT approach; distinguish upstream libGDX feature support from actual *Forge* compatibility. Briefly note if a Java-backend/browser-client split could become a fallback, without researching hosting/accounts/UI design.
5. Propose the smallest experiment compiling a **real Forge Adventure source component and assets** into a browser, ideally demonstrable on Safari portrait; specify changed modules, commands and explicit pass/fail criteria.

## Deliverable
Write only `docs/research/results/codex-runtime.md` on branch `research/codex-runtime` (or a uniquely named branch). Sections:
- Executive feasibility assessment (evidence vs unknown)
- Exact entry points, modules, relevant classes and assets, with paths
- Baseline build commands and results; environment constraints
- Ranked dependency/browser blocker matrix
- Browser technologies comparison and recommended experiment
- Go / no-go criteria, risks and next task
- Links/commit versions for external libraries where relevant

**Restrictions:** no implementation, large downloads/system installs, game-code changes, cloud services, network exposure, unrelated refactors, or edits to any other agent's findings or shared STATUS. Make a draft PR for the report if possible. Do not claim Safari compatibility or a working Forge build without actually verifying them.
