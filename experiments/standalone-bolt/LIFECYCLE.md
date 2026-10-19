# D3c lifecycle ordering witness

Separate, unexecuted extension of the accepted PR #25 standalone diagnostic.
It uses original Forge controller gameplay and passive JDK shared-memory JDI
inspection. There is no projector, await-entry hold or network listener.

See the [implementation and one-run review plan](../../docs/experiments/bolt-lifecycle-witness-plan.md).
The new Java sources are not yet compiled or run. Do not execute the launcher
before explicit owner approval. The original `StandaloneBolt` entry point,
launcher and historical evidence remain unchanged.
