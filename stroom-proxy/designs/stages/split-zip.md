# Detailed Design — Split Zip Stage

[← Back to architecture overview](../architecture.md)

## 1. Purpose

The split zip stage takes multi-feed zip files (file groups containing data from more than one feed) and splits them into one output file group per feed. This ensures downstream aggregation is always per-feed.

This is an **optional** stage — if all incoming data is single-feed, the receive stage routes directly to pre-aggregate, bypassing split zip entirely.

## 2. Class Diagram

```mermaid
classDiagram
    class SplitZipStageProcessor {
        -FileStoreRegistry fileStoreRegistry
        -FileStore outputStore
        -FileGroupQueue outputQueue
        -String sourceNodeId
        -SplitFunction splitFunction
        +process(FileGroupQueueItem)
        -copyDirectoryContents(Path, Path)$
        -deleteRecursively(Path)$
    }

    class FileGroupQueueItemProcessor {
        <<interface>>
        +process(FileGroupQueueItem)
    }

    class SplitFunction {
        <<functional interface>>
        +split(Path sourceDir, Path outputParentDir)
    }

    class FileStoreRegistry {
        +resolve(FileGroupQueueMessage) Path
        +requireFileStore(String) FileStore
    }

    SplitZipStageProcessor ..|> FileGroupQueueItemProcessor
    SplitZipStageProcessor --> SplitFunction
    SplitZipStageProcessor --> FileStoreRegistry
    SplitZipStageProcessor --> FileStore : outputStore
    SplitZipStageProcessor --> FileGroupQueue : outputQueue
```

## 3. Constructor Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `fileStoreRegistry` | `FileStoreRegistry` | Yes | Resolves input message locations to local paths |
| `outputStore` | `FileStore` | Yes | Output file store for split results (`splitStore`) |
| `outputQueue` | `FileGroupQueue` | Yes | Output queue (`preAggregateInput`) |
| `sourceNodeId` | `String` | Yes | Node identifier for message provenance |
| `splitFunction` | `SplitFunction` | Yes | Pluggable function that performs the actual zip splitting |

## 4. Processing Sequence

```mermaid
sequenceDiagram
    participant W as Worker
    participant SZSP as SplitZipStageProcessor
    participant FSR as FileStoreRegistry
    participant SF as SplitFunction
    participant OS as splitStore
    participant OQ as preAggregateInput
    participant IS as Input FileStore

    W->>SZSP: process(item)
    SZSP->>FSR: resolve(message)
    FSR-->>SZSP: sourceDir
    SZSP->>SZSP: createTempDirectory("split-zip-")
    SZSP->>SF: split(sourceDir, tempSplitDir)
    
    loop For each split output directory
        SZSP->>OS: newWrite()
        SZSP->>SZSP: copyDirectoryContents(splitDir, write.getPath())
        SZSP->>OS: write.commit()
        OS-->>SZSP: FileStoreLocation
        SZSP->>SZSP: Build FileGroupQueueMessage
        SZSP->>OQ: publish(outMessage)
    end

    SZSP->>IS: delete(inputLocation)
    SZSP->>SZSP: deleteRecursively(tempSplitDir)
    SZSP-->>W: return
```

### Step-by-step

1. **Resolve input** — Uses `FileStoreRegistry` to resolve the input message's `FileStoreLocation` to a local directory path. Validates it is a directory.

2. **Create staging directory** — Creates `split-zip-<n>` under
   `<path.temp>/pipeline/splitZip`, resolved through `TempDirProvider`.

   Temp is the correct home for this data: it is purely transient, deleted as soon
   as the split completes, and never needs to survive a restart. Being
   memory-backed is usually an advantage for what is a copy-heavy operation.

   What matters is that it goes through `TempDirProvider` rather than
   `java.io.tmpdir` directly, so it honours the proxy's `path.temp` setting. Using
   the raw system temp directory put staging outside the proxy's configured paths,
   where nothing cleaned it after a hard kill and no operator would think to look.

   The stage clears its staging root at startup, as `CleanupDirQueue` does for
   `99_deleting`, so an ungraceful stop cannot leave directories accumulating. The
   root must therefore not be shared with another proxy process.

   **Sizing.** The *whole* split of a multi-feed file group lands here before any
   of it is committed. If `path.temp` resolves to a small tmpfs and your file
   groups are large, point `path.temp` at disk.

3. **Delegate splitting** — Calls `splitFunction.split(sourceDir, tempSplitDir)`. The function writes one child directory per feed into `tempSplitDir`.

4. **Publish each split** — Iterates over child directories of `tempSplitDir`. For each:
   - Opens a `FileStoreWrite` on the output store
   - Copies the split directory contents
   - Commits the write
   - Creates a new `FileGroupQueueMessage` with a fresh UUID and the `splitZip` producing stage
   - Publishes to the output queue

5. **Delete input** — Deletes the consumed input from the source file store (ownership transfer).

6. **Cleanup temp** — Deletes the temporary split directory in a `finally` block.

## 5. SplitFunction — Production Wiring

In production, the `SplitFunction` is wired by `ProxyPipelineAssembler` to delegate to the existing `ZipSplitter`:

```java
(sourceDir, outputParentDir) -> {
    FileGroup fileGroup = new FileGroup(sourceDir);
    AttributeMap attributeMap = new AttributeMap();
    AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
    Map<FeedKey, List<ZipEntryGroup>> allowedEntries =
        ZipEntryGroup.read(fileGroup.getEntries())
            .stream()
            .collect(Collectors.groupingBy(ZipEntryGroup::getFeedKey));
    ZipSplitter.splitZip(
        fileGroup.getZip(), attributeMap, allowedEntries, outputParentDir);
}
```

This reads the meta file for attributes, groups entries by feed key, and calls the well-tested `ZipSplitter.splitZip()` static method.

## 6. Fan-Out Behaviour

Unlike other stages that produce one output per input, split zip produces **N outputs for 1 input** (one per feed). Each output gets its own `FileGroupQueueMessage` with a unique `fileGroupId` but shares the same `traceId` from the source message for correlation.

```mermaid
flowchart LR
    subgraph Input
        I["Multi-feed zip\n(feeds: A, B, C)"]
    end
    subgraph Outputs
        O1["Feed A file group"]
        O2["Feed B file group"]
        O3["Feed C file group"]
    end
    I --> O1
    I --> O2
    I --> O3
```

## 7. Error Handling

- If the `splitFunction` throws, the exception propagates to the `FileGroupQueueWorker`, which calls `item.fail(error)`. The message is returned to the queue for retry.
- The `finally` block ensures the temporary directory is always cleaned up.
- If some splits have been published but a later split fails, the input message
  is retried and the whole split runs again, so already-published splits are
  **re-published**. This is at-least-once behaviour and is accepted; nothing
  suppresses the duplicates. Note that each re-run assigns fresh random
  `fileGroupId`s to its splits, so the duplicates cannot be correlated with the
  originals — unlike a redelivered forward, where the `fileGroupId` is carried
  through from the source message.

## 8. Acknowledgement Contract

The processor does **not** call `item.acknowledge()` or `item.fail()`. The enclosing `FileGroupQueueWorker` owns acknowledgement:
- If `process()` returns normally → worker calls `item.acknowledge()`
- If `process()` throws → worker calls `item.fail(error)`
