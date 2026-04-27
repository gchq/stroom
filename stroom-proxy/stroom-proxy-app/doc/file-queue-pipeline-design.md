# Stroom Proxy File-Queue Pipeline Design

## Status

This document describes the current Stroom Proxy file-queue pipeline as implemented in the `stroom-proxy` modules.

It focuses on the runtime topology that is built from the proxy application entry point and then follows the data path through:

- ingress and receipt handling,
- file-group creation,
- filesystem-backed queues,
- optional pre-aggregation and aggregation,
- forwarding,
- retry and failure handling,
- side ingress paths such as event store, SQS, and directory scanning.

The purpose of this document is to capture the functional components that operate on either side of the file queues and to provide a design view of how those components are wired together.

## Scope

This document is concerned with the current file-queue based proxy pipeline. It does not attempt to fully describe older database-backed proxy repository designs, except where those concepts help explain the role of the newer file-group and directory queue stages.

The main code areas covered are:

- `stroom-proxy-app/src/main/java/stroom/proxy/app`
- `stroom-proxy-app/src/main/java/stroom/proxy/app/guice`
- `stroom-proxy-app/src/main/java/stroom/proxy/app/handler`
- `stroom-proxy-app/src/main/java/stroom/proxy/app/event`
- `stroom-proxy-repo/src/main/java/stroom/proxy/repo`
- `stroom-proxy-repo/src/main/java/stroom/proxy/repo/queue`
- `stroom-proxy-repo/src/main/java/stroom/proxy/repo/store`
- selected shared receive classes in `stroom-receive/stroom-receive-common`

## Architectural summary

Stroom Proxy is a Dropwizard application that uses Guice to assemble a data pipeline. Data enters through one of several ingress adapters, is normalised into proxy file groups, and is handed between stages using durable directory-backed queues.

At a high level:

1. `App` starts the Dropwizard application.
2. `ProxyModule` and `ProxyCoreModule` configure runtime bindings.
3. `ReceiverFactoryProvider` builds the receive pipeline based on `ProxyConfig`.
4. `ProxyLifecycle` starts `ProxyServices`.
5. `ProxyServices` starts queue transfer workers and scheduled tasks.
6. Receive-side components write file groups to queues.
7. Queue consumers pre-aggregate, aggregate, or forward file groups.
8. Forward destinations send file groups to downstream HTTP or file destinations.
9. Retry wrappers maintain per-destination forward, retry, and failure queues.

The core design boundary is the filesystem-backed `DirQueue`.

Before a queue, components are generally concerned with:

- protocol handling,
- authentication,
- receipt policy,
- metadata creation,
- stream normalisation,
- file-group creation.

After a queue, components are generally concerned with:

- asynchronous processing,
- durable handoff,
- aggregation,
- fan-out,
- forwarding,
- retry,
- cleanup.

## Entry point and bootstrap components

### `App`

Path:

- `stroom-proxy-app/src/main/java/stroom/proxy/app/App.java`

Responsibilities:

- Dropwizard application entry point.
- Sets YAML configuration source provider.
- Enables strict YAML deserialisation.
- Creates validation-only injector for config/resource validation.
- Validates `ProxyConfig`.
- Creates the main Guice injector using `ProxyModule`.
- Registers Dropwizard metrics, admin tasks, health checks, filters, servlets, admin servlets, REST resources, and managed services.
- Logs proxy build/runtime information and configured forwarders.

Important runtime action:

- `App.run(...)` constructs `ProxyModule`, creates the Guice injector, injects members into `App`, and registers the managed services that ultimately start `ProxyLifecycle`.

### `ProxyModule`

Path:

- `stroom-proxy-app/src/main/java/stroom/proxy/app/guice/ProxyModule.java`

Responsibilities:

- Main proxy Guice module.
- Binds Dropwizard environment objects.
- Installs:
  - `ProxyConfigModule`
  - `ProxyCoreModule`
  - `DropwizardModule`
  - `ForwarderModule`
  - `PrometheusModule`
- Registers servlet bindings:
  - `ReceiveDataServlet`
  - `DebugServlet`
  - `ProxyStatusServlet`
  - `ProxyWelcomeServlet`
- Registers admin servlet bindings:
  - `ProxyQueueMonitoringServlet`
  - `FilteredHealthCheckServlet`
- Registers REST resources:
  - receive data rule resources,
  - feed status resources,
  - event ingest resource.
- Binds managed services:
  - `ProxyLifecycle`
  - `RemoteFeedStatusService`
  - `RefreshManager`.

### `ProxyCoreModule`

Path:

- `stroom-proxy-app/src/main/java/stroom/proxy/app/guice/ProxyCoreModule.java`

Responsibilities:

- Core proxy service bindings.
- Installs:
  - `RemoteFeedModule`
  - `EventStoreModule`
  - `TaskContextModule`
  - `ProxyJerseyModule`
  - `ProxySecurityModule`
  - `ProxyCacheServiceModule`
  - `QueueModule`
  - `StoreModule`
- Binds:
  - `RequestHandler` to `ProxyRequestHandler`
  - `ReceiptIdGenerator` to `ProxyReceiptIdGenerator`
  - `FeedStatusService` to `RemoteFeedStatusService`
  - `ReceiveDataRuleSetService` to `RemoteReceiveDataRuleSetServiceImpl`
  - `DataReceiptPolicyAttributeMapFilterFactory` to `DataReceiptPolicyAttributeMapFilterFactoryImpl`
  - `DataDirProvider` to `DataDirProviderImpl`
  - `ProgressLog` to `ProgressLogImpl`
- Provides singleton `ReceiverFactory` via `ReceiverFactoryProvider`.

### `ProxyLifecycle`

Path:

- `stroom-proxy-app/src/main/java/stroom/proxy/app/ProxyLifecycle.java`

Responsibilities:

- Dropwizard `Managed` lifecycle.
- Adds scheduled executors for:
  - rolling event store files,
  - forwarding event store files,
  - scanning directories for proxy zip files,
  - polling SQS connectors if configured.
- Starts and stops `ProxyServices`.

### `ProxyServices`

Path:

- `stroom-proxy-repo/src/main/java/stroom/proxy/repo/ProxyServices.java`

Responsibilities:

- Central runtime executor manager.
- Maintains:
  - `ParallelExecutor` instances for blocking/repeating queue workers,
  - `FrequencyExecutor` instances for scheduled work.
- Starts and stops all registered executors.

## Bootstrap UML

~~~plantuml
@startuml
title Stroom Proxy bootstrap

actor Operator

Operator -> App : main(args)
App -> App : parse YAML path
App -> App : initialise Dropwizard bootstrap
App -> App : validate ProxyConfig
App -> ProxyModule : new ProxyModule(config, environment, configFile)
App -> Guice : createInjector(ProxyModule)
Guice -> ProxyModule : configure()
ProxyModule -> ProxyCoreModule : install()
ProxyModule -> ForwarderModule : install()
ProxyModule -> DropwizardModule : install()
ProxyModule -> PrometheusModule : install()
ProxyCoreModule -> ReceiverFactoryProvider : provide ReceiverFactory
App -> ManagedServices : register()
ManagedServices -> ProxyLifecycle : start()
ProxyLifecycle -> ProxyServices : start()

@enduml
~~~

## Functional component map

The current file-queue pipeline can be understood as the following component groups.

| Component group | Primary classes | Responsibility |
| --- | --- | --- |
| Application bootstrap | `App`, `ProxyModule`, `ProxyCoreModule`, `ProxyLifecycle` | Start the proxy, validate configuration, build runtime graph, start executors. |
| Runtime executor management | `ProxyServices`, `ParallelExecutor`, `FrequencyExecutor` | Run queue workers and scheduled tasks. |
| HTTP receive ingress | `ReceiveDataServlet`, `ProxyRequestHandler` | Accept `/datafeed` requests and stream request bodies into receivers. |
| Receive authentication/policy | `RequestAuthenticatorImpl`, `DataReceiptPolicyAttributeMapFilterFactoryImpl`, `RemoteReceiveDataRuleSetServiceImpl`, `RemoteFeedStatusService` | Authenticate requests and decide receive/drop/reject policy. |
| Receiver factory/pipeline assembly | `ReceiverFactoryProvider`, `ReceiverFactory`, `StoringReceiverFactory` | Construct the pipeline variant from config and choose simple or zip receivers. |
| File-group creation | `SimpleReceiver`, `ZipReceiver`, `ZipSplitter`, `FileGroup`, `ProxyZipWriter` | Convert incoming streams/zips into normal proxy file groups. |
| Queue infrastructure | `DirQueue`, `DirQueueFactory`, `DirQueueTransfer`, `DirNames` | Durable filesystem handoff between stages. |
| Queue/store monitoring | `QueueMonitors`, `QueueMonitor`, `FileStores`, `ProxyQueueMonitoringServlet` | Expose queue positions and file-store metrics. |
| Pre-aggregation | `PreAggregator` | Group file groups by feed/type and close pre-aggregates by size/count/age. |
| Aggregation | `Aggregator` | Merge closed pre-aggregates into final forwardable file groups. |
| Forwarding orchestration | `Forwarder`, `MultiForwardDestination` | Send file groups to one or more destinations. |
| Destination retry | `RetryingForwardDestination`, `RetryState` | Maintain per-destination forward/retry/failure queues. |
| HTTP destination | `ForwardHttpPostDestinationFactoryImpl`, `ForwardHttpPostDestination`, `HttpSender` | Send file groups downstream over HTTP. |
| File destination | `ForwardFileDestinationFactoryImpl`, `ForwardFileDestinationImpl` | Move file groups to configured filesystem destinations. |
| Side ingress | `EventResourceImpl`, `EventStore`, `SqsConnector`, `ZipDirScanner` | Inject event, SQS, or scanned zip data into the same receiver pipeline. |
| Cleanup | `CleanupDirQueue` | Move directories into delete area and recursively remove them. |
| Operational logging | `LogStream`, `ProgressLog` | Record receive/send/drop/reject/error progress. |

## File-group contract

A proxy file group is the unit of data passed between queue-backed stages. It is normally represented as a directory containing a consistent set of files.

The common files are:

| File | Purpose |
| --- | --- |
| `proxy.zip` or equivalent zip file | Contains data, context, and/or meta entries in proxy zip format. |
| `proxy.meta` or equivalent metadata file | Holds stream-level metadata in `AttributeMap` form. |
| `proxy.entries` or equivalent entries file | Holds discovered zip entry grouping information used by split/aggregate stages. |
| `error.log` | Optional failure diagnostics, generally associated with retry/failure handling. |
| retry state file | Optional binary state used by retry handling to record attempts and timestamps. |

The exact file names are represented through `FileGroup` and related helper classes rather than hard-coded throughout the pipeline.

Important design rules:

1. A file group must be complete before it is handed to a queue.
2. Queue handoff is normally by directory move.
3. After handoff, the downstream stage owns the directory.
4. Successful consumers either:
   - move the directory to another queue,
   - move it to a destination,
   - or delete it through cleanup.
5. Failed consumers should leave enough diagnostic state for retry or operator investigation.

## Directory and queue names

Queue and working directory names are centralised in `DirNames`.

| Name | Directory | Kind | Purpose |
| --- | --- | --- | --- |
| `RECEIVING_SIMPLE` | `01_receiving_simple` | temporary receive area | Temporary location for receiving non-zip/simple data. |
| `RECEIVING_ZIP` | `01_receiving_zip` | temporary receive area | Temporary location for receiving zip data. |
| `SPLIT_ZIP_QUEUE` | `02_split_zip_input_queue` | queue | Queue for zips that need to be split by feed/type. |
| `SPLIT_ZIP` | `03_split_zip_splits` | temporary work area | Temporary output area for split zip content. |
| `PRE_AGGREGATE_INPUT_QUEUE` | `20_pre_aggregate_input_queue` | queue | Queue before pre-aggregation. |
| `PRE_AGGREGATES` | `21_pre_aggregates` | durable work area | Area holding open pre-aggregates. |
| `PRE_AGGREGATE_SPLITTING` | `22_splitting` | temporary work area | Temporary splitting area used by pre-aggregation. |
| `PRE_AGGREGATE_SPLIT_OUTPUT` | `23_split_output` | staged work area | Output from pre-aggregate splitting. |
| `AGGREGATE_INPUT_QUEUE` | `30_aggregate_input_queue` | queue | Queue before final aggregation. |
| `AGGREGATES` | `31_aggregates` | temporary work area | Area where final aggregate zip file groups are formed. |
| `FORWARDING_INPUT_QUEUE` | `40_forwarding_input_queue` | queue | Queue before forwarding. |
| `FORWARDING` | `50_forwarding` | destination work area | Per-destination forwarding/retry/failure area. |

## `DirQueue` design

`DirQueue` is the main durable handoff abstraction.

Responsibilities:

- Maintain a root directory for queue items.
- Assign monotonically increasing item IDs.
- Add items using atomic directory moves where possible.
- Block consumers waiting for `next()`.
- Recover existing queued items during startup by scanning the queue root.
- Maintain read/write positions.
- Update `QueueMonitor`.
- Register the queue root as a file store with `FileStores`.

Important characteristics:

- Queue items are directories.
- Queue paths are numbered.
- Producers do not pass streams directly to consumers.
- Consumers process durable filesystem state.
- Restart behaviour is based on what remains on disk.
- Queue monitoring is built into the queue lifecycle.

## Queue handoff UML

~~~plantuml
@startuml
title Directory queue handoff

participant Producer
participant "DirQueue" as Queue
participant "QueueMonitor" as Monitor
participant "Consumer worker\nDirQueueTransfer" as Transfer
participant Consumer

Producer -> Queue : add(sourceDir)
Queue -> Queue : allocate next write id
Queue -> Queue : atomic move sourceDir to queue path
Queue -> Monitor : setWritePos(id)
Queue -> Transfer : signal item available

loop worker
  Transfer -> Queue : next()
  Queue -> Monitor : setReadPos(id)
  Queue --> Transfer : Dir
  Transfer -> Consumer : accept(dir.path)
end

@enduml
~~~

## Main runtime pipeline variants

The runtime receive pipeline is assembled by `ReceiverFactoryProvider`.

It supports three main variants:

1. normal non-aggregating queue-backed pipeline,
2. normal aggregating queue-backed pipeline,
3. instant forwarding pipeline.

### Variant A: non-aggregating queue-backed pipeline

This variant is used when aggregation is disabled and no instant forwarder is configured.

Flow:

1. Data is received.
2. `SimpleReceiver` or `ZipReceiver` writes a complete file group.
3. The receiver adds the file-group directory to `40_forwarding_input_queue`.
4. A queue transfer worker passes directories to `Forwarder`.
5. `Forwarder` sends to one or more destinations.

~~~plantuml
@startuml
title Non-aggregating proxy file-queue pipeline

actor Sender

Sender -> ReceiveDataServlet : POST/PUT datafeed
ReceiveDataServlet -> ProxyRequestHandler : handle(request, response)
ProxyRequestHandler -> ReceiverFactory : get(attributeMap)
ReceiverFactory -> StoringReceiverFactory : choose receiver
StoringReceiverFactory --> ProxyRequestHandler : SimpleReceiver or ZipReceiver

ProxyRequestHandler -> Receiver : receive(inputStream, attributeMap)
Receiver -> Receiver : write file group under 01_receiving_*
Receiver -> "40_forwarding_input_queue" : add(fileGroupDir)

"Forwarding queue worker" -> "40_forwarding_input_queue" : next()
"40_forwarding_input_queue" --> "Forwarding queue worker" : fileGroupDir
"Forwarding queue worker" -> Forwarder : add(fileGroupDir)
Forwarder -> "Forward destination(s)" : add(fileGroupDir)

@enduml
~~~

### Variant B: aggregating queue-backed pipeline

This variant is used when aggregation is enabled and no instant forwarder is configured.

Flow:

1. Data is received.
2. `SimpleReceiver` or `ZipReceiver` writes a complete file group.
3. The receiver adds the file-group directory to `20_pre_aggregate_input_queue`.
4. A queue transfer worker passes file groups to `PreAggregator`.
5. `PreAggregator` groups file groups by feed/type.
6. Closed pre-aggregate directories are added to `30_aggregate_input_queue`.
7. A queue transfer worker passes closed aggregate directories to `Aggregator`.
8. `Aggregator` creates a final forwardable file group.
9. The final file group is added to `40_forwarding_input_queue`.
10. A queue transfer worker passes file groups to `Forwarder`.
11. `Forwarder` sends to destinations.

~~~plantuml
@startuml
title Aggregating proxy file-queue pipeline

actor Sender

Sender -> ReceiveDataServlet : POST/PUT datafeed
ReceiveDataServlet -> ProxyRequestHandler : handle(request, response)
ProxyRequestHandler -> ReceiverFactory : get(attributeMap)
ReceiverFactory --> ProxyRequestHandler : SimpleReceiver or ZipReceiver
ProxyRequestHandler -> Receiver : receive(inputStream, attributeMap)

Receiver -> Receiver : write file group under 01_receiving_*
Receiver -> "20_pre_aggregate_input_queue" : add(fileGroupDir)

"Pre-aggregate worker" -> "20_pre_aggregate_input_queue" : next()
"20_pre_aggregate_input_queue" --> "Pre-aggregate worker" : fileGroupDir
"Pre-aggregate worker" -> PreAggregator : addDir(fileGroupDir)
PreAggregator -> "21_pre_aggregates" : add to open aggregate by feed/type
PreAggregator -> "30_aggregate_input_queue" : add(closedAggregateDir)

"Aggregate worker" -> "30_aggregate_input_queue" : next()
"30_aggregate_input_queue" --> "Aggregate worker" : closedAggregateDir
"Aggregate worker" -> Aggregator : addDir(closedAggregateDir)
Aggregator -> "31_aggregates" : create final aggregate file group
Aggregator -> "40_forwarding_input_queue" : add(aggregateFileGroupDir)

"Forwarding worker" -> "40_forwarding_input_queue" : next()
"40_forwarding_input_queue" --> "Forwarding worker" : fileGroupDir
"Forwarding worker" -> Forwarder : add(fileGroupDir)
Forwarder -> "Forward destination(s)" : add(fileGroupDir)

@enduml
~~~

### Variant C: split zip branch

`ZipReceiver` can identify zip data that needs splitting, for example because it contains multiple feeds or is not already in the expected normal form.

Flow:

1. `ZipReceiver` receives and examines zip input.
2. Input requiring splitting is added to `02_split_zip_input_queue`.
3. `ZipSplitter` creates separate file groups under `03_split_zip_splits`.
4. Split outputs are handed to the configured next destination:
   - `20_pre_aggregate_input_queue`, if aggregation is enabled,
   - or `40_forwarding_input_queue`, if aggregation is disabled.

~~~plantuml
@startuml
title Zip splitting branch

ZipReceiver -> ZipReceiver : receive and inspect zip
ZipReceiver -> "02_split_zip_input_queue" : add(zipDir)

"Split zip worker" -> "02_split_zip_input_queue" : next()
"02_split_zip_input_queue" --> "Split zip worker" : zipDir
"Split zip worker" -> ZipSplitter : split(zipDir)
ZipSplitter -> "03_split_zip_splits" : create split file groups

alt aggregation enabled
  ZipSplitter -> "20_pre_aggregate_input_queue" : add(splitFileGroupDir)
else aggregation disabled
  ZipSplitter -> "40_forwarding_input_queue" : add(splitFileGroupDir)
end

@enduml
~~~

### Variant D: instant forwarding pipeline

If exactly one enabled forward destination has `instant=true`, `ReceiverFactoryProvider` builds an instant forwarding receiver factory.

In this mode, the normal durable queue pipeline is bypassed.

Important consequences:

- Only one enabled destination is allowed.
- Aggregation is not part of the normal queue-backed pipeline.
- Forwarding errors can be returned directly to the sender.
- This mode is conceptually a repeater/direct forwarding mode.

Primary classes:

- `InstantForwardHttpPost`
- `InstantForwardFile`

~~~plantuml
@startuml
title Instant forwarding pipeline

actor Sender

Sender -> ReceiveDataServlet : POST/PUT datafeed
ReceiveDataServlet -> ProxyRequestHandler : handle(request, response)
ProxyRequestHandler -> ReceiverFactory : get(attributeMap)

alt instant HTTP
  ReceiverFactory -> InstantForwardHttpPost : get(config)
  ProxyRequestHandler -> "Instant HTTP receiver" : receive(inputStream, attributeMap)
  "Instant HTTP receiver" -> "Downstream HTTP" : send immediately
else instant file
  ReceiverFactory -> InstantForwardFile : get(config)
  ProxyRequestHandler -> "Instant file receiver" : receive(inputStream, attributeMap)
  "Instant file receiver" -> "File destination" : write/move immediately
end

@enduml
~~~

## Receive-side components before queues

### HTTP receive path

| Class | Responsibility |
| --- | --- |
| `ReceiveDataServlet` | Servlet for datafeed POST/PUT requests. Delegates to `RequestHandler`. |
| `RequestHandler` | Shared receive abstraction. |
| `ProxyRequestHandler` | Proxy implementation of `RequestHandler`; creates attributes, authenticates, validates, chooses receiver, streams body. |
| `ProxyReceiptIdGenerator` | Generates proxy receipt IDs. |
| `DataReceiptMetrics` | Records receive timing and content metrics. |

`ProxyRequestHandler` is the main receive orchestration point. Its responsibilities include:

- creating an `AttributeMap`,
- generating receipt IDs,
- authenticating the request,
- validating compression,
- applying configured size and metadata constraints,
- selecting a `Receiver` through `ReceiverFactory`,
- streaming the request body to the receiver,
- writing receipt information to the response.

### Authentication and policy

| Class | Responsibility |
| --- | --- |
| `RequestAuthenticatorImpl` | Authenticates datafeed requests using configured auth mechanisms. |
| `ReceiveDataConfig` | Configures receive behaviour, authentication, request limits, allowed metadata, and receipt policy mode. |
| `DataReceiptPolicyAttributeMapFilterFactoryImpl` | Creates filters that apply receive/drop/reject policy. |
| `RemoteReceiveDataRuleSetServiceImpl` | Fetches receive rules from a downstream Stroom/Stroom Proxy instance. |
| `RemoteFeedStatusService` | Provides feed status checks using downstream services. |

Policy outcomes are generally:

| Outcome | Meaning |
| --- | --- |
| receive | Continue into file-group creation and queue pipeline. |
| drop | Consume and discard the data while logging the drop. |
| reject | Reject the request and signal failure to the sender. |

### Receiver selection

| Class | Responsibility |
| --- | --- |
| `ReceiverFactoryProvider` | Builds the runtime receiver factory and wires queue destinations. |
| `ReceiverFactory` | Interface used by request handling to choose a receiver. |
| `StoringReceiverFactory` | Selects `SimpleReceiver` or `ZipReceiver` based on compression. |
| `SimpleReceiver` | Handles simple/plain/gzip input and writes proxy zip file groups. |
| `ZipReceiver` | Handles incoming proxy zip input and validates/splits/normalises as required. |
| `ZipSplitter` | Splits zips that contain multiple feed/type groups or require normalisation. |
| `DropReceiver` | Handles receive policy drop outcomes. |

## Receive-side UML

~~~plantuml
@startuml
title Receive side before file queues

actor Sender
participant ReceiveDataServlet
participant ProxyRequestHandler
participant RequestAuthenticatorImpl
participant "Receipt policy filter" as Policy
participant ReceiverFactory
participant StoringReceiverFactory
participant SimpleReceiver
participant ZipReceiver
queue "Next DirQueue" as NextQueue

Sender -> ReceiveDataServlet : POST/PUT
ReceiveDataServlet -> ProxyRequestHandler : handle()
ProxyRequestHandler -> ProxyRequestHandler : create AttributeMap
ProxyRequestHandler -> ProxyRequestHandler : generate receipt id
ProxyRequestHandler -> RequestAuthenticatorImpl : authenticate()
RequestAuthenticatorImpl --> ProxyRequestHandler : identity/auth metadata
ProxyRequestHandler -> ReceiverFactory : get(attributeMap)
ReceiverFactory -> StoringReceiverFactory : choose by compression

alt simple/plain/gzip
  StoringReceiverFactory --> ProxyRequestHandler : SimpleReceiver
  ProxyRequestHandler -> SimpleReceiver : receive(stream, attributes)
  SimpleReceiver -> Policy : check(attributes)
  SimpleReceiver -> SimpleReceiver : write proxy file group
  SimpleReceiver -> NextQueue : add(fileGroupDir)
else zip
  StoringReceiverFactory --> ProxyRequestHandler : ZipReceiver
  ProxyRequestHandler -> ZipReceiver : receive(stream, attributes)
  ZipReceiver -> Policy : check(attributes)
  ZipReceiver -> ZipReceiver : validate/normalise zip
  ZipReceiver -> NextQueue : add(fileGroupDir)
end

@enduml
~~~

## Queue-backed processing components

### `PreAggregator`

Path:

- `stroom-proxy-app/src/main/java/stroom/proxy/app/handler/PreAggregator.java`

Responsibilities:

- Consume individual file groups.
- Group them by feed and type.
- Maintain open aggregate state in `21_pre_aggregates`.
- Optionally split oversized source data to improve aggregate sizing.
- Close aggregates when configured limits are reached:
  - maximum item count,
  - maximum uncompressed byte size,
  - maximum age.
- Add closed aggregate directories to the next destination, usually `30_aggregate_input_queue`.
- Schedule periodic closing of old aggregates using `ProxyServices`.

Important supporting state:

- `AggregateState`
- `FeedKey`
- metrics histograms for aggregate item count, byte size, and age.

### `Aggregator`

Path:

- `stroom-proxy-app/src/main/java/stroom/proxy/app/handler/Aggregator.java`

Responsibilities:

- Consume closed pre-aggregate directories.
- If a closed aggregate has one source file group, pass it through without merging.
- If a closed aggregate has multiple source file groups:
  - create a new aggregate file group under `31_aggregates`,
  - merge zip entries,
  - preserve common metadata,
  - write final aggregate zip/meta files.
- Add the resulting file group to the next destination, usually `40_forwarding_input_queue`.
- Delete source aggregate directories via `CleanupDirQueue`.

### Processing-stage ownership

The queue-backed processing stages follow a common ownership pattern:

1. A stage receives ownership of a directory from a queue.
2. The stage creates any required output directory under its own working area.
3. Once output is complete, it hands the output directory to the next queue or destination.
4. The stage cleans up source directories when they are no longer needed.

This pattern reduces the chance of downstream stages observing partial output.

## Forwarding-side components after queues

### `Forwarder`

Path:

- `stroom-proxy-app/src/main/java/stroom/proxy/app/handler/Forwarder.java`

Responsibilities:

- Build enabled destinations from `ProxyConfig`.
- Use a single destination directly if only one is configured.
- Use `MultiForwardDestination` if multiple destinations are configured.
- Accept file-group directories from `40_forwarding_input_queue`.

### `MultiForwardDestination`

Responsibilities:

- Fan out one source file group to multiple destinations.
- Create temporary copies for destination-specific processing.
- Send one copy to each configured destination.
- Delete the original only if all destinations succeed.
- Use `CleanupDirQueue` for deletion.

### `RetryingForwardDestination`

Responsibilities:

- Wrap a concrete destination with retry/failure handling.
- Maintain per-destination queue directories under `50_forwarding`.
- Record retry state.
- Move failed items to retry or failure areas.
- Honour retry limits, retry age, delay, and liveness checks.
- Pause/resume work based on destination health where configured.

A typical per-destination forwarding area contains conceptual sub-queues such as:

| Area | Purpose |
| --- | --- |
| forward queue | Items waiting for initial forwarding. |
| retry queue | Items waiting for retry after recoverable failures. |
| failure area | Items that exceeded retry policy or failed non-recoverably. |

Exact directory names are owned by the forwarding/retry implementation.

### HTTP forwarding

| Class | Responsibility |
| --- | --- |
| `ForwardHttpPostDestinationFactoryImpl` | Creates HTTP destination and wraps it in retry handling. |
| `ForwardHttpPostDestination` | Reads file-group metadata and streams zip data to a downstream HTTP endpoint. |
| `HttpSender` | Performs HTTP POST and liveness GET, adds auth/metadata headers, classifies failures. |
| `ForwardHttpPostConfig` | HTTP destination configuration. |

### File forwarding

| Class | Responsibility |
| --- | --- |
| `ForwardFileDestinationFactoryImpl` | Creates file destination and wraps it in retry handling. |
| `ForwardFileDestinationImpl` | Moves file-group directories to configured output filesystem location. |
| `ForwardFileConfig` | File destination configuration. |

## Forwarding UML

~~~plantuml
@startuml
title Forwarding side after 40_forwarding_input_queue

queue "40_forwarding_input_queue" as ForwardQueue
participant "Forwarding queue worker" as Worker
participant Forwarder
participant MultiForwardDestination
participant RetryingForwardDestination
participant ForwardHttpPostDestination
participant ForwardFileDestinationImpl
participant CleanupDirQueue

Worker -> ForwardQueue : next()
ForwardQueue --> Worker : fileGroupDir
Worker -> Forwarder : add(fileGroupDir)

alt one destination
  Forwarder -> RetryingForwardDestination : add(fileGroupDir)
else multiple destinations
  Forwarder -> MultiForwardDestination : add(fileGroupDir)
  MultiForwardDestination -> MultiForwardDestination : copy per destination
  MultiForwardDestination -> RetryingForwardDestination : add(copyDir)
end

alt HTTP destination
  RetryingForwardDestination -> ForwardHttpPostDestination : add(dir)
  ForwardHttpPostDestination -> HttpSender : POST zip + headers
else file destination
  RetryingForwardDestination -> ForwardFileDestinationImpl : add(dir)
  ForwardFileDestinationImpl -> ForwardFileDestinationImpl : move to destination path
end

RetryingForwardDestination -> CleanupDirQueue : cleanup on success

@enduml
~~~

## Side ingress paths

Not all data enters via `/datafeed`. Several side ingress paths feed the same receive/file-group pipeline.

### Event API and event store

| Class | Responsibility |
| --- | --- |
| `EventResourceImpl` | REST resource for event ingest. |
| `EventStore` | Appends events to store files, rolls files, forwards rolled files through receiver pipeline. |
| `EventStoreConfig` | Configures event store behaviour. |

`ProxyLifecycle` schedules:

- event store roll,
- event store forward.

### SQS connector

| Class | Responsibility |
| --- | --- |
| `SqsConnector` | Polls SQS and writes messages as events. |
| `SqsConnectorConfig` | Configures polling and queue behaviour. |

`ProxyLifecycle` creates and schedules `SqsConnector` instances when configured.

### ZIP directory scanner

| Class | Responsibility |
| --- | --- |
| `ZipDirScanner` | Scans configured directories for zip files and sidecar metadata, then injects them through the zip receiver path. |
| `DirScannerConfig` | Configures scan directories, failure directory, enablement, and scan frequency. |

The scanner is useful when files are placed into a directory by another process and proxy is responsible for picking them up and forwarding or aggregating them.

## Side ingress UML

~~~plantuml
@startuml
title Side ingress paths into the receiver pipeline

participant EventResourceImpl
participant SqsConnector
participant EventStore
participant ZipDirScanner
participant ReceiverFactory
participant ZipReceiver
queue "Next queue\n20_pre_aggregate_input_queue or\n40_forwarding_input_queue" as NextQueue

EventResourceImpl -> EventStore : append(event)
SqsConnector -> EventStore : append(message as event)
EventStore -> EventStore : roll files
EventStore -> ReceiverFactory : forward rolled event data

ZipDirScanner -> ZipDirScanner : scan configured dirs
ZipDirScanner -> ZipReceiver : receive scanned zip

ReceiverFactory -> ZipReceiver : choose receiver
ZipReceiver -> NextQueue : add(fileGroupDir)

@enduml
~~~

## End-to-end pipeline component diagram

~~~plantuml
@startuml
title Stroom Proxy file-queue component model

package "Bootstrap" {
  [App]
  [ProxyModule]
  [ProxyCoreModule]
  [ProxyLifecycle]
  [ProxyServices]
}

package "Ingress" {
  [ReceiveDataServlet]
  [EventResourceImpl]
  [SqsConnector]
  [ZipDirScanner]
}

package "Receive service" {
  [ProxyRequestHandler]
  [RequestAuthenticatorImpl]
  [Receipt policy]
  [ReceiverFactoryProvider]
  [StoringReceiverFactory]
  [SimpleReceiver]
  [ZipReceiver]
  [ZipSplitter]
}

package "Queues and stores" {
  queue "02_split_zip_input_queue" as QSplit
  queue "20_pre_aggregate_input_queue" as QPreAgg
  queue "30_aggregate_input_queue" as QAgg
  queue "40_forwarding_input_queue" as QForward
  [DirQueue]
  [DirQueueTransfer]
  [QueueMonitors]
  [FileStores]
}

package "Processing" {
  [PreAggregator]
  [Aggregator]
}

package "Forwarding" {
  [Forwarder]
  [MultiForwardDestination]
  [RetryingForwardDestination]
  [ForwardHttpPostDestination]
  [ForwardFileDestinationImpl]
  [HttpSender]
}

package "Cleanup and operations" {
  [CleanupDirQueue]
  [LogStream]
  [ProgressLog]
  [ProxyQueueMonitoringServlet]
}

[App] --> [ProxyModule]
[ProxyModule] --> [ProxyCoreModule]
[ProxyModule] --> [ProxyLifecycle]
[ProxyLifecycle] --> [ProxyServices]

[ReceiveDataServlet] --> [ProxyRequestHandler]
[ProxyRequestHandler] --> [RequestAuthenticatorImpl]
[ProxyRequestHandler] --> [Receipt policy]
[ProxyRequestHandler] --> [StoringReceiverFactory]
[StoringReceiverFactory] --> [SimpleReceiver]
[StoringReceiverFactory] --> [ZipReceiver]

[EventResourceImpl] --> [EventStore]
[SqsConnector] --> [EventStore]
[EventStore] --> [ReceiverFactoryProvider]
[ZipDirScanner] --> [ZipReceiver]

[ZipReceiver] --> QSplit
QSplit --> [ZipSplitter]
[SimpleReceiver] --> QPreAgg
[ZipReceiver] --> QPreAgg
[ZipSplitter] --> QPreAgg

[SimpleReceiver] --> QForward
[ZipReceiver] --> QForward
[ZipSplitter] --> QForward

QPreAgg --> [PreAggregator]
[PreAggregator] --> QAgg
QAgg --> [Aggregator]
[Aggregator] --> QForward

QForward --> [Forwarder]
[Forwarder] --> [MultiForwardDestination]
[Forwarder] --> [RetryingForwardDestination]
[MultiForwardDestination] --> [RetryingForwardDestination]
[RetryingForwardDestination] --> [ForwardHttpPostDestination]
[RetryingForwardDestination] --> [ForwardFileDestinationImpl]
[ForwardHttpPostDestination] --> [HttpSender]

[PreAggregator] --> [CleanupDirQueue]
[Aggregator] --> [CleanupDirQueue]
[RetryingForwardDestination] --> [CleanupDirQueue]

[DirQueue] --> [QueueMonitors]
[DirQueue] --> [FileStores]
[ProxyQueueMonitoringServlet] --> [QueueMonitors]
[ProxyQueueMonitoringServlet] --> [FileStores]

@enduml
~~~

## Runtime wiring by `ReceiverFactoryProvider`

`ReceiverFactoryProvider` is the most important class for understanding how the pipeline is assembled.

It evaluates:

- enabled forward destination count,
- enabled instant forward destination count,
- aggregation enabled/disabled,
- configured worker thread counts.

### Startup validation

It enforces key runtime constraints:

- at least one enabled forward destination must exist,
- instant forwarding cannot be combined with additional enabled destinations,
- exactly one enabled instant forwarder must exist when instant mode is selected.

### Non-instant setup

When instant forwarding is not configured, it always creates:

- `Forwarder`
- `40_forwarding_input_queue`
- a `DirQueueTransfer` from `40_forwarding_input_queue` to `Forwarder.add(...)`
- a parallel executor named for forwarding queue transfer.

Then it branches:

- if aggregation is disabled:
  - receivers write directly to `40_forwarding_input_queue`;
- if aggregation is enabled:
  - receivers write to `20_pre_aggregate_input_queue`;
  - `PreAggregator` writes to `30_aggregate_input_queue`;
  - `Aggregator` writes to `40_forwarding_input_queue`.

### Aggregating setup

Aggregation setup creates:

- `Aggregator`
- `30_aggregate_input_queue`
- aggregate input transfer worker
- `PreAggregator`
- `20_pre_aggregate_input_queue`
- pre-aggregate input transfer worker
- `SimpleReceiver` and `ZipReceiver` targeting `20_pre_aggregate_input_queue`.

### Non-aggregating setup

Non-aggregating setup creates:

- `SimpleReceiver` targeting `40_forwarding_input_queue`
- `ZipReceiver` targeting `40_forwarding_input_queue`
- `StoringReceiverFactory`

### Instant setup

Instant setup creates:

- `InstantForwardHttpPost` receiver factory for HTTP instant destination,
- or `InstantForwardFile` receiver factory for file instant destination.

No normal queue transfer workers are created for the receive/aggregate/forward-input path in this mode.

## Operational observability

### Queue monitoring

`DirQueue` instances update `QueueMonitor` values for read and write positions.

`ProxyQueueMonitoringServlet` exposes queue and file store state to operators.

Useful operational questions answered by queue monitoring include:

- Is the receive side producing faster than downstream processing?
- Is pre-aggregation keeping up?
- Is final aggregation keeping up?
- Is forwarding blocked?
- Are retry/failure queues growing?
- Which store directories are consuming disk?

### File store monitoring

`FileStores` tracks registered directories and associated file counts/sizes. Queue roots and important processing areas can be registered so disk growth is visible.

### Log stream

`LogStream` records operational send/receive/drop/reject/error information. It is used by receive/drop/forwarding components to provide a structured audit trail of proxy behaviour.

## Failure handling design

Failure handling depends on where the failure occurs.

### Receive-time failure

Examples:

- authentication failure,
- reject policy,
- invalid compression,
- malformed zip,
- IO failure while receiving.

Expected outcome:

- request fails,
- sender sees an error response,
- partial temporary receive state is cleaned where possible,
- failure is logged.

### Drop policy

If receipt policy resolves to drop:

- the proxy consumes or discards the input,
- records receive/drop information,
- does not place data onto downstream queues,
- may still return a successful receipt depending on policy semantics.

### Queue processing failure

If a worker fails while processing a queued directory:

- the directory should remain on disk unless explicitly moved,
- operational logs should identify the failing stage,
- retry behaviour depends on the stage implementation.

### Forwarding failure

Forwarding has the most explicit retry model:

- recoverable failures may be moved to retry queues,
- non-recoverable failures may be moved to failure areas,
- retry state records attempts and timestamps,
- retry/failure handling is destination-specific,
- liveness checks can pause forwarding work.

### Cleanup

`CleanupDirQueue` reduces the risk of deleting directories in place by moving them into a delete area before recursive deletion.

## Design principles embodied in the current pipeline

### 1. Durable handoff between major stages

The pipeline avoids direct in-memory handoff between receive, aggregation, and forwarding stages. Instead, durable directories are handed off through `DirQueue`.

### 2. Complete-before-visible output

Stages create output in temporary or working locations and only hand it to the next queue/destination when complete.

### 3. Stage ownership

Once a directory is handed to a queue or destination, ownership transfers. The producer should not mutate it after handoff.

### 4. Restart recovery through filesystem state

Queue state and in-progress work are primarily recoverable by examining the filesystem during startup.

### 5. Configurable topology

The runtime topology changes based on config:

- instant vs queue-backed,
- aggregating vs non-aggregating,
- one destination vs multiple destinations,
- HTTP vs file destinations,
- side ingress enabled/disabled.

### 6. Destination-specific retry

Retry/failure state is maintained close to each destination, allowing different destinations to have independent retry behaviour.

## Areas to consider for future improvement

### Make pipeline topology explicit

`ReceiverFactoryProvider` currently builds much of the topology imperatively. A future design could introduce a more explicit pipeline model, for example:

- `PipelineTopology`
- `PipelineStage`
- `QueueBackedStage`
- `StageConnector`

This would make it easier to render, test, and validate the runtime graph.

### Strengthen file-group contract documentation

The file-group structure is central to the design. It would be useful to document:

- exact file naming rules,
- required vs optional files,
- `.entries` format,
- metadata inheritance/normalisation rules,
- error/retry sidecar file formats.

### Add topology-level validation

Some validation exists in configuration and startup checks. A topology validator could verify:

- every queue has a producer and consumer,
- every configured destination is reachable,
- aggregation settings are consistent,
- instant mode is mutually exclusive with queue-backed multi-destination mode,
- side ingress paths have valid downstream receivers.

### Improve operator diagrams

The queue monitoring servlet could potentially expose a topology view based on the same model described in this document:

- queue names,
- current read/write positions,
- lag,
- file count,
- byte size,
- producer stage,
- consumer stage,
- destination health.

### Clarify failure semantics per stage

Forwarding has clear retry semantics, but pre-forward stages could benefit from explicit documented failure-state behaviour:

- malformed file group,
- failed pre-aggregation,
- failed aggregation,
- failed cleanup,
- scanner ingestion failure.

## Glossary

| Term | Meaning |
| --- | --- |
| file group | Directory containing a proxy zip and sidecar metadata/entries files. |
| proxy zip | Zip file using the proxy conventions for data/meta/context entries. |
| `DirQueue` | Filesystem-backed queue where each item is a directory. |
| receive side | Components that accept external input and create file groups. |
| processing side | Queue consumers that split, pre-aggregate, aggregate, or transform file groups. |
| forwarding side | Components that send file groups to configured destinations. |
| instant forwarding | Direct forwarding mode that bypasses the normal durable queue pipeline. |
| pre-aggregate | A feed/type-specific collection of file groups being accumulated before final aggregation. |
| aggregate | Final merged forwardable file group produced from one or more source file groups. |
| destination | A configured HTTP or file output target. |
| retry queue | Destination-specific queue for failed items that may be retried. |
| failure area | Destination-specific area for items that will not currently be retried. |

## Key classes by area

### Bootstrap

- `stroom.proxy.app.App`
- `stroom.proxy.app.guice.ProxyModule`
- `stroom.proxy.app.guice.ProxyCoreModule`
- `stroom.proxy.app.ProxyLifecycle`
- `stroom.proxy.repo.ProxyServices`

### Receive

- `stroom.receive.common.ReceiveDataServlet`
- `stroom.receive.common.RequestHandler`
- `stroom.proxy.app.handler.ProxyRequestHandler`
- `stroom.receive.common.RequestAuthenticatorImpl`
- `stroom.proxy.app.handler.ProxyReceiptIdGenerator`
- `stroom.proxy.app.handler.ReceiverFactory`
- `stroom.proxy.app.handler.ReceiverFactoryProvider`
- `stroom.proxy.app.handler.StoringReceiverFactory`
- `stroom.proxy.app.handler.SimpleReceiver`
- `stroom.proxy.app.handler.ZipReceiver`
- `stroom.proxy.app.handler.ZipSplitter`
- `stroom.proxy.app.handler.DropReceiver`

### Queues and stores

- `stroom.proxy.app.handler.DirQueue`
- `stroom.proxy.app.handler.DirQueueFactory`
- `stroom.proxy.app.handler.DirQueueTransfer`
- `stroom.proxy.app.handler.DirNames`
- `stroom.proxy.repo.queue.QueueMonitor`
- `stroom.proxy.repo.queue.QueueMonitors`
- `stroom.proxy.repo.store.FileStores`

### Aggregation

- `stroom.proxy.app.handler.PreAggregator`
- `stroom.proxy.app.handler.Aggregator`
- `stroom.proxy.repo.AggregatorConfig`
- `stroom.proxy.repo.FeedKey`

### Forwarding

- `stroom.proxy.app.handler.Forwarder`
- `stroom.proxy.app.handler.ForwarderModule`
- `stroom.proxy.app.handler.ForwardDestination`
- `stroom.proxy.app.handler.MultiForwardDestination`
- `stroom.proxy.app.handler.RetryingForwardDestination`
- `stroom.proxy.app.handler.RetryState`
- `stroom.proxy.app.handler.ForwardHttpPostDestinationFactoryImpl`
- `stroom.proxy.app.handler.ForwardHttpPostDestination`
- `stroom.proxy.app.handler.HttpSender`
- `stroom.proxy.app.handler.ForwardHttpPostConfig`
- `stroom.proxy.app.handler.ForwardFileDestinationFactoryImpl`
- `stroom.proxy.app.handler.ForwardFileDestinationImpl`
- `stroom.proxy.app.handler.ForwardFileConfig`

### Side ingress

- `stroom.proxy.app.event.EventResourceImpl`
- `stroom.proxy.app.event.EventStore`
- `stroom.proxy.app.event.EventStoreConfig`
- `stroom.proxy.app.SqsConnector`
- `stroom.proxy.app.SqsConnectorConfig`
- `stroom.proxy.app.handler.ZipDirScanner`
- `stroom.proxy.app.DirScannerConfig`

### Operations

- `stroom.proxy.app.servlet.ProxyQueueMonitoringServlet`
- `stroom.proxy.repo.LogStream`
- `stroom.proxy.repo.ProgressLog`
- `stroom.proxy.app.handler.CleanupDirQueue`

## Summary

The Stroom Proxy file-queue pipeline is best understood as a configurable set of durable, filesystem-backed stages.

The most important design boundary is `DirQueue`. Components before a queue transform incoming streams into complete file groups. Components after a queue process, aggregate, forward, retry, or clean up those file groups.

The key runtime wiring is concentrated in `ReceiverFactoryProvider`, with lifecycle and worker execution provided by `ProxyLifecycle` and `ProxyServices`.

The primary queue-backed paths are:

- receive to forwarding:
  - `01_receiving_*` -> `40_forwarding_input_queue` -> forwarding,
- receive to aggregation to forwarding:
  - `01_receiving_*` -> `20_pre_aggregate_input_queue` -> `30_aggregate_input_queue` -> `40_forwarding_input_queue` -> forwarding,
- zip split branch:
  - `01_receiving_zip` -> `02_split_zip_input_queue` -> `03_split_zip_splits` -> next queue,
- destination retry:
  - `40_forwarding_input_queue` -> `50_forwarding/<destination>/...`.

Instant forwarding is a separate direct mode and should be treated as a queue-bypass topology.