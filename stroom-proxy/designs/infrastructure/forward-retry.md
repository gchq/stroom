# Forward retry and failure handling

**Superseded, 2026-09-09.** Retry, back-off, liveness and give-up are designed with the forward
stage in [stages/forward.md](../stages/forward.md): a group stays claimed on the pipeline queue until
it is delivered or given up on, and the node-local retry tier this document described goes. What
that tier did is recorded in [forward.md §7](../stages/forward.md#7-what-the-implementation-before-this-design-did).
