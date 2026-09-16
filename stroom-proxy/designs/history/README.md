# Planning History

> **These documents are historical.** They are the plans the pluggable-queue
> pipeline was built from, not a description of what was built. Where they
> disagree with the documents in the parent directory, the parent directory is
> correct.
>
> They are kept because they record *why* — the options considered, the
> trade-offs weighed, and the constraints that shaped the result. That rationale
> is not recoverable from the code.

Expect these to describe classes that were renamed or never created, package
layouts that predate the `config`/`queue`/`store`/`stage`/`runtime`/`monitor`
split, and proposals that were implemented differently or dropped.

## Contents

### [pluggable-queue-implementation-plan.md](pluggable-queue-implementation-plan.md)

The originating plan. Sets out the goals and non-goals, the queue and
`FileStore` abstractions, the reference-message contract, the ownership-transfer
rule, the stage input/output contracts, the config model, and a phased
implementation order.

Most valuable for the sections that argue rather than specify: why references
rather than data on the queue, why the write→publish→delete→acknowledge ordering
is the one that loses nothing, and the acknowledgement-mapping table across the
three backends.

Note it predates the decision to make the pipeline the only path — it discusses
coexistence with the `ReceiverFactoryProvider`/`DirQueue` architecture, and
`ReceiverFactoryProvider` no longer exists.

### [s3-filestore-implementation-plan.md](s3-filestore-implementation-plan.md)

Plan for the S3 `FileStore` backend: config model extensions, the object
layout, credentials handling, factory dispatch, validation and test strategy.
The confirmed design decisions section is the useful part.

Class paths in this document predate the package reorganisation, and the
proposed test class names differ from those built
(`AbstractFileStoreContractTest`, `TestLocalFileStoreContract`,
`TestS3FileStoreContract`).

### [s3-streaming-optimisation-plan.md](s3-streaming-optimisation-plan.md)

Per-stage analysis of where S3 reads and writes actually occur, and which could
avoid local staging. Its conclusion — that the benefit is smaller than it looks
because every stage reads the whole file group — is recorded as an open item in
[../future-work.md](../future-work.md) §3.

## What Was Removed

`implementation-audit.md`, a point-in-time conformance check of the
implementation against the plan above, was deleted during the documentation
reorganisation. It had served its purpose — the plan is implemented — and it
referenced the plan by line number, which no longer holds.
