# D3c lifecycle ordering witness

Separate extension of the accepted PR #25 standalone diagnostic.
It uses original Forge controller gameplay and passive JDK shared-memory JDI
inspection. There is no projector, await-entry hold or network listener.

See the [implementation and one-run review plan](../../docs/experiments/bolt-lifecycle-witness-plan.md).
The [single owner-authorized attempt](../../docs/experiments/bolt-lifecycle-witness-results.md)
compiled successfully but stopped at the launcher's child executable/entry-point
guard. The trace is empty; no gameplay/ordering pass is claimed. No fix or retry
was performed during that attempt. A new attempt requires separate owner approval.
The Java witness source, original `StandaloneBolt` entry point and historical
evidence remain unchanged. The separately approved
[launcher amendment](../../docs/experiments/bolt-lifecycle-launcher-correction.md)
adds privacy-safe identity diagnostics and explicit literal matching; it has only
static verification and has not been executed. Fresh execution needs separate
owner approval after review.
