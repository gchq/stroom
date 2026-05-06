# Breaking Change Log

All breaking changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]
* **Proxy pipeline is now always active.** The `pipeline.enabled` YAML key has been removed. The pipeline unconditionally starts all 5 stages (receive → split-zip → pre-aggregate → aggregate → forward) with local filesystem queues and stores by default. Existing configs that set `pipeline.enabled: false` will see an unknown-property warning on startup. To disable individual stages in a distributed deployment, set `stages.<name>.enabled: false` per stage.
* **`threads` config block removed.** The top-level `threads` (a.k.a. `ThreadConfig`) configuration has been removed. Thread settings are now per-stage via `stages.<name>.threads.consumerThreads`, `stages.<name>.threads.maxConcurrentReceives`, etc.
* **Stage-specific config classes replace `PipelineStageConfig`.** The generic `PipelineStageConfig` and `PipelineStageThreadsConfig` classes have been removed. Each stage now has its own typed configuration class (`ReceiveStageConfig`, `SplitZipStageConfig`, `PreAggregateStageConfig`, `AggregateStageConfig`, `ForwardStageConfig`) with fields specific to that stage's role. Thread configuration is also typed: `ReceiveStageThreadsConfig` (`maxConcurrentReceives`), `ConsumerStageThreadsConfig` (`consumerThreads`), and `PreAggregateStageThreadsConfig` (`consumerThreads` + `closeOldAggregatesThreads`).
* **Stage processors moved to per-stage packages.** Stage processor classes have moved from `stroom.proxy.app.pipeline.stage` to per-stage sub-packages (`stage.receive`, `stage.splitzip`, `stage.preaggregate`, `stage.aggregate`, `stage.forward`). Code importing these classes directly will need updated imports.

## [v7.3]
* StroomQL `vis as` keyword combination replaced with `show`.

## [v7.2]

* Quoted strings in dashboard table expressions can now be expressed with single and double quotes. As part of this change apostrophes in text are no longer escaped with `''` but instead require a leading `\` before them if they are in a single quoted string. In many cases it is preferable to use double quotes if the string in question has an apostrophe. Note that the use of `\` as an escape character also means that any existing `\` characters will need to be escaped with a preceding `\` so `\` must now become `\\`.  