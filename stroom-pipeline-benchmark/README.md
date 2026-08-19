# stroom-pipeline-benchmark

Correctness and performance coverage for a minimal XML-in / XML-out pipeline:

```
XMLParser -> SplitFilter -> XSLTFilter -> XMLWriter -> FileAppender
```

It is the same translation as `stroom.pipeline.TestXMLTransformer` in `stroom-app` (the same
`records:2` input shape and the same `records -> event-logging:3` stylesheet), reduced to the five
elements above and scaled up to a million records so that the cost of the `SplitFilter` settings is
visible.

## Why a separate module

The existing pipeline tests live in `stroom-app` and extend `AbstractProcessIntegrationTest`. That
needs no database, but it does need JUnit's Guice extension and it clones the `stroom-content`
repository to install XML schemas — neither of which a forked JMH JVM can reasonably do.

`PipelineBenchmarkModule` instead assembles just the pipeline machinery, closely following
`stroom.headless.CliModule`, with in-memory document storage. No database, no content packs, no
JUnit. `XmlTransformHarness` can therefore be constructed directly by a benchmark, which is what
lets JMH fork properly.

## Layout

| Class | Purpose |
| --- | --- |
| `XMLEventsDataGenerator` | Writes `records:2` XML with a unique timestamp, LineNo, User and Message per record. |
| `BenchmarkPaths` | Resolves where generated input and output live. |
| `PipelineBenchmarkModule` | Guice module providing the pipeline machinery without a database. |
| `XmlTransformHarness` | Builds the pipeline for a given split count and runs it. |
| `TransformOutputVerifier` | Streams the output and reports event counts, LineNo coverage and digests. |
| `TestXmlTransformSplit` | The correctness test, with indicative timings. |
| `XmlTransformSplitBenchmark` | The JMH benchmark. |

## Running the test

```bash
./gradlew :stroom-pipeline-benchmark:test
```

Defaults to 1,000,000 records at split counts 1, 10, 100, 1000, 10000, 100000 and 1000000, with the
split depth fixed at 1. For a quicker run:

```bash
./gradlew :stroom-pipeline-benchmark:test -Dstroom.benchmark.records=10000
```

`translatesRecordsToEvents` checks the translation at a scale where the whole output can be parsed
by a DOM parser. `splitCountChangesBatchingButNotOutput` runs every split count and asserts, for
each, that there are exactly N events, that every LineNo from 1 to N appears exactly once, and that
the output is a single well-formed document — then that all seven outputs are byte identical.

## Running the benchmark

```bash
./gradlew :stroom-pipeline-benchmark:jmh
./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="-f 3 -wi 2 -i 5"
./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="-p recordCount=100000 -p splitCount=1,1000"
```

One invocation processes the whole file, so the benchmark uses `SingleShotTime` rather than
throughput mode. The injector, document store entries and compiled stylesheet are built once per
trial, so what is measured is a pipeline run rather than start-up.

## Generated data

Input and output live under `stroom-pipeline-benchmark/build/benchmark-data`, so `./gradlew clean`
removes them. A million records is about 236MB of input and about 790MB of output. The input is
generated once and reused, including by forked JMH JVMs; the tests delete each output as they go so
that only one is on disk at a time. Override the location with
`-Dstroom.benchmark.dataDir=/some/path`.

The data is deliberately not committed: it is too large, and it is cheap to regenerate (about a
second for a million records).

## What the split count does, and does not, change

`splitDepth=1` splits on the children of the `records` root, so each group is a whole number of
records. `splitCount` is how many records go into each group, and therefore how many records the
XSLT sees per invocation; `splitCount=0` means no splitting at all.

The `XMLWriter` folds the groups back into a single output document, so the split count does not
change the output at all — every accepted split count produces byte identical bytes. What it changes
is how often the transform is invoked and how much is held in memory at once.

## Measured results

One run of each split count over 1,000,000 records (246,666,889 bytes of input), on a 16-core
machine with an 8GB test heap. Every accepted run produced identical output: 800,666,928 bytes,
1,000,000 events, digest `6ff301181d84…`.

| splitCount | duration | records/sec |
| ---: | ---: | ---: |
| 1 | 54.0s | 18,516 |
| 10 | 45.4s | 22,047 |
| 100 | 43.3s | 23,105 |
| 1000 | 42.5s | 23,531 |
| 10000 | 38.2s | 26,202 |
| 100000 | 38.8s | 25,750 |
| 1000000 | rejected | — |

The spread is modest — about 40% between the slowest and the fastest — and it flattens out well
before the largest usable group. Splitting one record at a time costs the most, as each record pays
a full transform set-up; the gain from batching is largely spent by 10,000 records per group.

## Where the `splitCount=1` penalty comes from

`SplitOverheadAttributionBenchmark` runs the pipeline with and without the `XSLTFilter`, at split 1
and split 100, over 100,000 records. Removing the XSLT element isolates what the surrounding
pipeline costs on its own:

| | split 1 | split 100 | penalty | penalty/record |
| --- | ---: | ---: | ---: | ---: |
| with XSLT | 3882ms / 4.34GB | 3082ms / 2.91GB | 800ms / 1.44GB | 8.0µs / 14.4KB |
| without XSLT | 642ms / 0.37GB | 528ms / 0.18GB | 114ms / 0.19GB | 1.1µs / 1.9KB |

Only about 14% of the penalty survives removing the XSLT element, so roughly **86% of the cost of
splitting at 1 is the XSLT element** — a new source tree and transform per record — and the rest is
the `SplitFilter` replaying buffered start events and the `XMLWriter` opening and closing a document.

`SaxonReuseBenchmark` then asks how much of that is recoverable by reusing Saxon machinery, driving
Saxon directly with SAX events for 1,000 records:

| strategy | time | allocated/record |
| --- | ---: | ---: |
| new `Transformer` per record (what `XsltFilter` does) | 8.255ms | 28.1KB |
| reuse one `Transformer`, new handler per record | 8.181ms | 26.2KB |
| reuse plus `reset()` between records | 8.675ms | 27.0KB |
| one batched transform | 6.857ms | 17.2KB |

Reusing the `Transformer` recovers under 7% of the excess allocation and no measurable time. The
rest cannot be reused: `TransformerHandlerImpl.startDocument()` throws
`"The TransformerHandler is not serially reusable"`, and `TinyBuilder.reset()` discards its
`TinyTree` outright. Saxon-HE has no supported way to reuse a source tree across transforms, and
XSLT 3.0 streaming — which would avoid building one at all — is Saxon-EE only
(`Configuration.makeStreamingTransformer` in the HE jar is a bare
`throw new XPathException("Streaming is only available in Saxon-EE")`).

## Can the source tree be reused? (research)

Yes, partly — and it needed correcting, because a first pass at this concluded it was impossible.

Saxon already does half the work. `TinyBuilder.open()` only allocates when its tree is null:

```
 8: getfield tree
12: ifnonnull 53      // non-null: skip the allocation entirely
15: new TinyTree ...
```

What gets in the way is `TinyBuilder.reset()`, which `TransformerHandlerImpl.endDocument()` calls
after every document and which sets that field back to null. `ReusableTinyTreeModel` keeps its own
reference and puts it back before each `open()`, reached through the supported hook
`Controller.setModel(TreeModel)` — `TreeModel` is public and abstract, and `Controller.makeBuilder()`
calls `treeModel.makeBuilder(pipe)` virtually.

Two things make this harder than it looks, both discovered the hard way:

- **`TinyTree` is `final`**, so it cannot be subclassed to add a reset or to suppress `condense()`.
- **The Saxon-HE jar is signed** (`META-INF/TE-050AC.SF`). Putting a helper class in
  `net.sf.saxon.tree.tiny` to reach the package-private members fails at class load with
  `"signer information does not match signer information of other classes in the same package"`.
  Reflection from our own package is the way in; it is legal because Saxon is on the class path and
  so lives in the unnamed module. `condense()` does not need suppressing after all — it only trims
  when `numberOfNodes * 3 < nodeKind.length`, which right-sized statistics avoid.

Measured over 1,000 records, with output verified identical to the non-reusing path:

| strategy | time | allocated/record |
| --- | ---: | ---: |
| new `Transformer` per record (what `XsltFilter` does) | 8.033ms | 28.10KB |
| reuse `Transformer` only | 8.261ms | 26.20KB |
| **reuse `Transformer` and tree** | **7.767ms** | **24.19KB** |
| one batched transform | 6.686ms | 17.34KB |

Tree reuse recovers about 36% of the excess allocation but only about 20% of the time gap, and
roughly 3% of total runtime. It works, it is just not where most of the cost is — the rest is the
result-side receiver chain, the per-document handler, and the XPath evaluation itself.

## Cold start is the bigger effect

`ColdStartTreeSizingExperiment` (`./gradlew :stroom-pipeline-benchmark:coldStart`) measures the
*first* records of a stream, which the JMH benchmarks cannot see because they run long enough for
Saxon's self-tuning `Statistics` to converge. Each run starts from a fresh `Configuration`:

| records | default | primed statistics | reused tree |
| ---: | ---: | ---: | ---: |
| 10 | 76,226 B | 30,217 B | 24,340 B |
| 100 | 39,714 B | 29,226 B | 24,184 B |
| 1,000 | 28,705 B | 27,385 B | 24,130 B |
| 10,000 | 26,542 B | 26,407 B | 24,128 B |

A ten-record stream allocates **76KB per record**, three times the steady-state figure, because the
shared `Statistics` starts at 4000 nodes and 4000 characters and takes a few thousand documents to
fall to its floor of 10. Priming it removes most of that for about five lines using only public API
(`Configuration.getTreeStatistics()` and `Statistics.updateStatistics(...)`) — no custom tree model,
no reflection. Reusing the tree is immune to the problem entirely, at a flat ~24KB.

How much this matters in production depends on pool churn: the `Statistics` lives on the
`Configuration`, which lives in the `XsltPool` entry, so it warms once per pooled XSLT and persists
while that entry is cached. Short streams and pool turnover are what expose it.

## The optimisations through the real pipeline (timings)

`SplitOptimisationBenchmark` runs the split count sweep through the actual Stroom pipeline once per
optimisation. 20,000 records, 12 measurement iterations, ms per run:

| splitCount | none | primedStats | reusedTree | both |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 791.1 ± 11.4 | 803.0 ± 24.5 | 797.1 ± 20.6 | 803.3 ± 28.9 |
| 10 | 639.6 ± 24.7 | 653.0 ± 27.2 | 666.4 ± 29.7 | 623.2 ± 16.7 |
| 100 | 640.1 ± 19.7 | 634.8 ± 13.3 | 629.6 ± 15.5 | 640.8 ± 14.7 |
| 1000 | 623.9 ± 9.6 | 605.2 ± 8.8 | 622.0 ± 9.2 | 608.2 ± 14.5 |
| 10000 | 607.2 ± 18.1 | 608.4 ± 23.4 | 610.4 ± 12.7 | 591.8 ± 10.0 |

**Neither optimisation produces a measurable improvement at any split count.** Every column agrees
within its error bars, and the ordering is not consistent between rows. The split count itself is
still the only thing that clearly matters: 791ms down to 607ms, about 23%.

That is consistent with, not contradicted by, the isolated results. Tree reuse saves about 3.3% of
Saxon's own time, and the attribution experiment puts the XSLT element at roughly 83% of pipeline
time, so the expected end-to-end saving is around 2.5% — some 20ms here, which sits inside a ±11 to
±29ms error bar. The effect is real but too small for this measurement to resolve, and far too small
to justify the correctness risk.

Priming the statistics cannot help a run like this either: 20,000 documents is long enough for
Saxon's statistics to converge on their own within the first few percent of the stream. Its benefit
is confined to short streams, which is what `coldStart` measures directly and this benchmark cannot.
Demonstrating it would need a benchmark of *many short streams*, not one long one.

An earlier run of the same sweep at 100,000 records with only 3 iterations was useless — error bars
of ±6905ms on a 4487ms score. Worth repeating: single-shot benchmarks of multi-second operations
need a lot of iterations before differences of a few percent mean anything.

## Conclusion

The split count remains the main lever — batching avoids costs that no amount of Saxon reuse
recovers. Tree reuse is real but small (~3% of runtime) and carries correctness caveats around
`id()`, `xsl:key` and returned source nodes. The cheapest genuine win found here is priming the
source-document statistics, which is public API and matters most for short streams.

## Why `splitCount=1000000` is rejected

`XsltConfig.maxElements` defaults to 1,000,000 and caps how many elements the `XSLTFilter` will
accept in a single transform. It exists to stop a pipeline without a usable splitter from running
out of memory, and it fails with:

```
Max element count of 1000000 has been exceeded. Please ensure a split filter is present and is
configured correctly for this pipeline.
```

Each generated record is seven elements (the `record` plus its six `data` children), so a group of
1,000,000 records is 7,000,001 elements — seven times the cap. Putting the whole file through one
transform is therefore not a supported configuration at this scale, and the test asserts the
rejection rather than omitting the case. The practical ceiling with this data is a little over
140,000 records per group; raising it means raising `maxElements`, which is exactly the memory
trade-off the cap is there to make you think about.
