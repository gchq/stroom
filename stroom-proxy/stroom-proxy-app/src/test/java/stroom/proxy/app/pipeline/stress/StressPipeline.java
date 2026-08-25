/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.pipeline.stress;

import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileGroupQueueFactory;
import stroom.proxy.app.pipeline.runtime.FileStoreFactory;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineRuntime;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.stage.PipelineStageRunner;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageProcessor;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStagePublisher;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageProcessor;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.time.StroomDuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A whole proxy pipeline on local disk, wrapped in fault injectors, driven hard.
 * <p>
 * This is the reusable part of the stress suite. It assembles the real
 * {@link ProxyPipelineRuntime} over the real local queues and local file stores,
 * decorating each with {@link FaultInjectingFileGroupQueue} and
 * {@link FaultInjectingFileStore}, and runs it with the real
 * {@link PipelineStageRunner}. Nothing about the transport, the durability or
 * the worker loop is simulated.
 * </p>
 *
 * <h2>What is real and what is a stand-in</h2>
 * <ul>
 *   <li><strong>Real:</strong> the queues, the stores, the queue worker, the
 *       stage runners and their backoff, the receive publisher, the split-zip
 *       stage processor and the forward stage processor.</li>
 *   <li><strong>Stand-in:</strong> the pre-aggregate and aggregate stages use
 *       {@link TransferStageProcessor}. Their real implementations batch many
 *       inputs into one output, which destroys the one-in-one-out accounting
 *       this harness relies on; aggregation itself is covered by its own
 *       tests.</li>
 *   <li><strong>Stand-in:</strong> the split function is a one-way pass-through
 *       that emits a single child group, so payload identity survives the stage.
 *       Real splitting is covered by {@code TestSplitZipStageProcessor}.</li>
 * </ul>
 *
 * <h2>Runner timings</h2>
 * <p>
 * The runners are built directly rather than through
 * {@code ProxyPipelineLifecycle}, which hard-codes production backoffs - a one
 * second failure backoff doubling to thirty. Under deliberate fault injection a
 * run would spend almost all of its wall-clock asleep and would test the
 * sleeping rather than the pipeline. The backoff <em>logic</em> is still the
 * real code and is exercised on every injected failure; only the durations are
 * scaled down.
 * </p>
 */
public final class StressPipeline implements AutoCloseable {

    public static final String NODE_ID = "stress-node";
    private static final int DEFAULT_BODY_SIZE = 512;

    /**
     * How long delivery may stand still before a quiesced pipeline is treated as
     * stalled rather than merely slow.
     */
    private static final Duration STALL_WINDOW = Duration.ofSeconds(3);

    private static final List<String> QUEUE_NAMES = List.of(
            ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
            ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
            ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
            ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);

    private static final List<String> STORE_NAMES = List.of(
            ProxyPipelineConfig.RECEIVE_STORE,
            ProxyPipelineConfig.SPLIT_STORE,
            ProxyPipelineConfig.PRE_AGGREGATE_STORE,
            ProxyPipelineConfig.AGGREGATE_STORE);

    private final Path root;
    private final FaultPolicy faultPolicy;
    private final DeliveryLedger ledger = new DeliveryLedger();
    private final ProxyPipelineConfig config;
    private final PathCreator pathCreator;
    private final int consumerThreads;

    private final Duration emptyPollBackoff = Duration.ofMillis(5);
    private final Duration errorBackoff = Duration.ofMillis(20);
    private final Duration failureBackoff = Duration.ofMillis(10);
    private final Duration maxFailureBackoff = Duration.ofMillis(100);

    private final AtomicInteger submitCounter = new AtomicInteger();
    private final AtomicInteger receiveRetryCount = new AtomicInteger();

    private FileGroupQueueFactory queueFactory;
    private FileStoreFactory storeFactory;
    private ProxyPipelineRuntime runtime;
    private ReceiveStagePublisher publisher;
    private List<PipelineStageRunner> runners = List.of();

    public StressPipeline(final Path root, final FaultPolicy faultPolicy) {
        this(root, faultPolicy, 1);
    }

    public StressPipeline(final Path root,
                          final FaultPolicy faultPolicy,
                          final int consumerThreads) {
        this.root = Objects.requireNonNull(root, "root");
        this.faultPolicy = Objects.requireNonNull(faultPolicy, "faultPolicy");
        this.consumerThreads = consumerThreads;
        this.config = new ProxyPipelineConfig(eagerReclaimQueues(), null, null);
        // The real path creator, rooted at the test directory, so relative store and
        // queue paths resolve exactly as they do in a deployment.
        this.pathCreator = new SimplePathCreator(() -> root, () -> root.resolve("temp"));
        assemble();
    }

    /**
     * Queue definitions that look for abandoned leases almost immediately.
     * <p>
     * The production default is ten seconds, which is right for a running proxy
     * and far too slow for a scenario that wants to see the reclaim happen.
     * Nothing else about the definitions is customised - the factory still
     * derives each queue's path from its logical name.
     * </p>
     */
    private static Map<String, QueueDefinition> eagerReclaimQueues() {
        final Map<String, QueueDefinition> queues = new LinkedHashMap<>();
        for (final String queueName : QUEUE_NAMES) {
            queues.put(queueName, new QueueDefinition(
                    QueueType.LOCAL_FILESYSTEM,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    StroomDuration.ofMillis(100),
                    null));
        }
        return queues;
    }

    public FaultPolicy getFaultPolicy() {
        return faultPolicy;
    }

    public DeliveryLedger getLedger() {
        return ledger;
    }

    public ProxyPipelineRuntime getRuntime() {
        return runtime;
    }

    /**
     * @return How many times a submission had to be retried because the receive
     * stage threw. A submitter that gave up instead would be losing data at the
     * entry point.
     */
    public int getReceiveRetryCount() {
        return receiveRetryCount.get();
    }

    // -------------------------------------------------------------------------
    // Assembly
    // -------------------------------------------------------------------------

    private void assemble() {
        this.queueFactory = new FaultInjectingQueueFactory(config, pathCreator, faultPolicy);
        this.storeFactory = new FaultInjectingStoreFactory(config, pathCreator, faultPolicy);

        final FileStoreRegistry registry = FileStoreRegistry.fromFactory(storeFactory);

        final Map<PipelineStageName, FileGroupQueueItemProcessor> processors =
                new EnumMap<>(PipelineStageName.class);

        processors.put(PipelineStageName.SPLIT_ZIP, new SplitZipStageProcessor(
                registry,
                storeFactory.getFileStore(ProxyPipelineConfig.SPLIT_STORE),
                queueFactory.getQueue(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE),
                NODE_ID,
                StressPipeline::passThroughSplit,
                root.resolve("split-tmp")));

        processors.put(PipelineStageName.PRE_AGGREGATE, new TransferStageProcessor(
                registry,
                storeFactory.getFileStore(ProxyPipelineConfig.PRE_AGGREGATE_STORE),
                queueFactory.getQueue(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE),
                PipelineStageName.PRE_AGGREGATE.getConfigName(),
                NODE_ID));

        processors.put(PipelineStageName.AGGREGATE, new TransferStageProcessor(
                registry,
                storeFactory.getFileStore(ProxyPipelineConfig.AGGREGATE_STORE),
                queueFactory.getQueue(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE),
                PipelineStageName.AGGREGATE.getConfigName(),
                NODE_ID));

        processors.put(PipelineStageName.FORWARD, new ForwardStageProcessor(
                registry,
                (message, sourceDir) -> ledger.recordDelivered(StressPayload.read(sourceDir))));

        this.runtime = ProxyPipelineRuntime.fromConfig(config, queueFactory, storeFactory, processors);

        this.publisher = new ReceiveStagePublisher(
                storeFactory.getFileStore(ProxyPipelineConfig.RECEIVE_STORE),
                queueFactory.getQueue(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE),
                queueFactory.getQueue(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE),
                NODE_ID);
    }

    /**
     * A split that does not split: one child group, contents copied verbatim.
     * Real fan-out would give several outputs one payload id, which the ledger
     * would read as duplicate delivery of a single submission.
     */
    private static void passThroughSplit(final Path sourceDir, final Path outputParentDir) throws IOException {
        TransferStageProcessor.copyDirectoryContents(sourceDir, outputParentDir.resolve("single"));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        final List<PipelineStageRunner> started = new ArrayList<>();
        for (final PipelineStageName stageName : List.of(
                PipelineStageName.SPLIT_ZIP,
                PipelineStageName.PRE_AGGREGATE,
                PipelineStageName.AGGREGATE,
                PipelineStageName.FORWARD)) {

            final FileGroupQueueWorker worker = runtime.getWorker(stageName).orElseThrow();
            final PipelineStageRunner runner = new PipelineStageRunner(
                    stageName,
                    worker,
                    consumerThreads,
                    emptyPollBackoff,
                    errorBackoff,
                    failureBackoff,
                    maxFailureBackoff);
            runner.start();
            started.add(runner);
        }
        this.runners = List.copyOf(started);
    }

    public void stop() {
        runners.forEach(runner -> runner.stop(Duration.ofSeconds(20)));
        this.runners = List.of();
    }

    /**
     * Simulate a process that died without warning, then came back.
     * <p>
     * The queues and stores are <em>not</em> closed - that is the point. A clean
     * close persists the local queue's id allocator and leaves nothing in
     * flight; a kill leaves both. Reassembly must therefore recover in-flight
     * messages back to pending and re-derive a safe id allocator by scanning,
     * rather than trusting a counter that was never written.
     * </p>
     */
    public void crashAndReopen() {
        final boolean wasRunning = !runners.isEmpty();
        stop();
        this.runtime = null;
        this.queueFactory = null;
        this.storeFactory = null;
        this.publisher = null;
        assemble();
        if (wasRunning) {
            start();
        }
    }

    /**
     * End the fault storm and give the pipeline every chance to catch up,
     * restarting it once if that is what it takes.
     * <p>
     * Asserting mid-storm only ever establishes "nothing was lost so far".
     * Quiescing first turns that into the much stronger "and then it caught up
     * completely", which is the property an operator actually cares about after
     * an outage.
     * </p>
     * <p>
     * The restart is a backstop, not the primary recovery path. If
     * {@code acknowledge()} or {@code fail()} throws,
     * {@code FileGroupQueueWorker} logs and rethrows and the item stays leased;
     * {@code LocalFileGroupQueue} now reclaims such messages while running, once
     * the consumer closes the item. The restart remains here because it is the
     * only thing that recovers a message whose consumer never closed it at all -
     * a hard kill - and because reporting a stall as data loss would be both
     * wrong and the more alarming of the two.
     * </p>
     */
    public Recovery quiesceAndDrain(final Duration beforeRestart,
                                    final Duration afterRestart) throws Exception {
        faultPolicy.quiesce();

        if (awaitFullDeliveryOrStall(beforeRestart, STALL_WINDOW)) {
            return new Recovery(true, false, 0);
        }

        final long stranded = countAllInFlight();
        crashAndReopen();
        final boolean drained = awaitFullDelivery(afterRestart);

        return new Recovery(drained, true, stranded);
    }

    /**
     * @param drained True if everything submitted was eventually delivered.
     * @param restartWasNeeded True if the pipeline only finished after a restart.
     * @param strandedInFlight How many items were leased-but-unfinished at the
     * point the restart was needed.
     */
    public record Recovery(boolean drained, boolean restartWasNeeded, long strandedInFlight) {

    }

    /**
     * Wait for full delivery, giving up early once the pipeline stops making
     * progress.
     * <p>
     * A fixed wait has to be long enough for the slowest legitimate drain, which
     * means every stalled run pays that whole wait for nothing. Watching the
     * delivery count instead distinguishes "still working" from "stuck" directly,
     * so a stalled scenario reaches its restart in seconds rather than minutes.
     * </p>
     *
     * @param timeout Overall limit.
     * @param stallWindow How long delivery may stand still before the pipeline is
     * considered stuck rather than slow.
     * @return True if everything was delivered.
     */
    public boolean awaitFullDeliveryOrStall(final Duration timeout,
                                            final Duration stallWindow) throws InterruptedException {
        final Instant deadline = Instant.now().plus(timeout);
        int lastProgress = ledger.getTotalDeliveryCount();
        Instant lastChange = Instant.now();

        while (Instant.now().isBefore(deadline)) {
            if (ledger.isFullyDelivered()) {
                return true;
            }

            final int progress = ledger.getTotalDeliveryCount();
            if (progress != lastProgress) {
                lastProgress = progress;
                lastChange = Instant.now();
            } else if (Duration.between(lastChange, Instant.now()).compareTo(stallWindow) > 0) {
                return false;
            }

            Thread.sleep(20);
        }
        return ledger.isFullyDelivered();
    }

    public long countAllInFlight() throws IOException {
        long total = 0;
        for (final String queueName : QUEUE_NAMES) {
            total += countJsonIn(localQueue(queueFactory.getQueue(queueName)).getInFlightDir());
        }
        return total;
    }

    /**
     * @return Where a named store keeps its data, for tests that need to reach in
     * and tamper with it.
     */
    public Path storeRootFor(final String storeName) {
        return storeRoot(storeFactory.getFileStore(storeName));
    }

    public long countPending(final String queueName) throws IOException {
        return countJsonIn(localQueue(queueFactory.getQueue(queueName)).getPendingDir());
    }

    public long countInFlight(final String queueName) throws IOException {
        return countJsonIn(localQueue(queueFactory.getQueue(queueName)).getInFlightDir());
    }

    @Override
    public void close() throws IOException {
        stop();
        if (runtime != null) {
            runtime.close();
        }
    }

    // -------------------------------------------------------------------------
    // Submission
    // -------------------------------------------------------------------------

    /**
     * Submit {@code count} single-feed payloads through the real receive stage.
     */
    public List<String> submit(final int count) throws IOException {
        return submit(count, false);
    }

    /**
     * @param viaSplitZip True to write multi-feed entries so the receive stage
     * routes the group through the split-zip queue.
     */
    public List<String> submit(final int count, final boolean viaSplitZip) throws IOException {
        final List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(submitOne(viaSplitZip));
        }
        return ids;
    }

    private String submitOne(final boolean viaSplitZip) throws IOException {
        final String payloadId = "payload-" + submitCounter.incrementAndGet();

        final Path incoming = Files.createDirectories(
                root.resolve("incoming").resolve(payloadId));
        StressPayload.write(incoming, payloadId, DEFAULT_BODY_SIZE);

        if (viaSplitZip) {
            // Two distinct feeds is what makes the receive stage choose the
            // split-zip queue.
            Files.writeString(
                    incoming.resolve("proxy.entries"),
                    "STRESS,RAW_EVENTS,1\nSTRESS_TWO,RAW_EVENTS,1\n");
        }

        // The receive stage's contract is that it only deletes its source once it
        // has published. A submitter that swallowed the exception here would be
        // the one losing the data, not the pipeline, so retry until it takes.
        while (true) {
            try {
                publisher.accept(incoming);
                ledger.recordSubmitted(payloadId);
                return payloadId;
            } catch (final UncheckedIOException e) {
                if (!isInjected(e)) {
                    throw e;
                }
                receiveRetryCount.incrementAndGet();
                if (!Files.isDirectory(incoming)) {
                    throw new IllegalStateException(
                            "Receive stage failed but deleted its source anyway: " + incoming, e);
                }
            }
        }
    }

    private static boolean isInjected(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof InjectedFaultException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Waiting and inspection
    // -------------------------------------------------------------------------

    /**
     * Wait until every submitted payload has been delivered at least once.
     *
     * @return True if the pipeline drained within the timeout.
     */
    public boolean awaitFullDelivery(final Duration timeout) throws InterruptedException {
        final Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (ledger.isFullyDelivered()) {
                return true;
            }
            Thread.sleep(20);
        }
        return ledger.isFullyDelivered();
    }

    /**
     * @return File groups still sitting in each store. After a drained run these
     * are orphans: committed data that no queue message references.
     */
    public Map<String, Long> countStoredFileGroups() throws IOException {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final String storeName : STORE_NAMES) {
            counts.put(storeName, countFileGroupsUnder(storeRoot(storeFactory.getFileStore(storeName))));
        }
        return counts;
    }

    private static Path storeRoot(final FileStore store) {
        // Ask the store where it lives rather than reconstructing the factory's
        // layout here - a layout change should not silently make these counts zero.
        final FileStore delegate = store instanceof final FaultInjectingFileStore injecting
                ? injecting.getDelegate()
                : store;

        if (delegate instanceof final LocalFileStore local) {
            return local.getRoot();
        }
        throw new IllegalStateException("Expected a LocalFileStore but got " + delegate.getClass());
    }

    private static long countFileGroupsUnder(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (final Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(path -> Files.isRegularFile(path)
                                         && "proxy.meta".equals(path.getFileName().toString()))
                    .count();
        }
    }

    /**
     * @return Total messages still sitting in any queue directory.
     */
    public long countQueuedMessages() throws IOException {
        long total = 0;
        for (final String queueName : QUEUE_NAMES) {
            final LocalFileGroupQueue queue = localQueue(queueFactory.getQueue(queueName));
            total += countJsonIn(queue.getPendingDir())
                     + countJsonIn(queue.getInFlightDir())
                     + countJsonIn(queue.getFailedDir());
        }
        return total;
    }

    /**
     * @return Messages parked in a queue's failed directory. The local queue
     * returns failed items to pending rather than quarantining them, so anything
     * here is a duplicate the queue declined to hand out twice, not lost data.
     */
    public long countFailedMessages() throws IOException {
        long total = 0;
        for (final String queueName : QUEUE_NAMES) {
            total += countJsonIn(localQueue(queueFactory.getQueue(queueName)).getFailedDir());
        }
        return total;
    }

    private static LocalFileGroupQueue localQueue(final FileGroupQueue queue) {
        final FileGroupQueue delegate = queue instanceof final FaultInjectingFileGroupQueue injecting
                ? injecting.getDelegate()
                : queue;

        if (delegate instanceof final LocalFileGroupQueue local) {
            return local;
        }
        throw new IllegalStateException("Expected a LocalFileGroupQueue but got " + delegate.getClass());
    }

    private static long countJsonIn(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        long total = 0;
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (final Path ignored : stream) {
                total++;
            }
        }
        return total;
    }

    public FileGroupQueue getQueue(final String queueName) {
        return queueFactory.getQueue(queueName);
    }

    // -------------------------------------------------------------------------
    // Fault-injecting factories
    // -------------------------------------------------------------------------

    private static final class FaultInjectingQueueFactory extends FileGroupQueueFactory {

        private final FaultPolicy faultPolicy;
        private final Map<String, FileGroupQueue> wrapped = new ConcurrentHashMap<>();

        private FaultInjectingQueueFactory(final ProxyPipelineConfig config,
                                           final PathCreator pathCreator,
                                           final FaultPolicy faultPolicy) {
            super(config, pathCreator);
            this.faultPolicy = faultPolicy;
        }

        @Override
        public FileGroupQueue getQueue(final String queueName) {
            // Must memoise: the runtime asks for the same logical queue from both
            // the producing and consuming stage, and two decorators over one
            // delegate would draw from the fault policy twice per operation.
            return wrapped.computeIfAbsent(
                    queueName,
                    name -> new FaultInjectingFileGroupQueue(super.getQueue(name), faultPolicy));
        }
    }

    private static final class FaultInjectingStoreFactory extends FileStoreFactory {

        private final FaultPolicy faultPolicy;
        private final Map<String, FileStore> wrapped = new ConcurrentHashMap<>();

        private FaultInjectingStoreFactory(final ProxyPipelineConfig config,
                                           final PathCreator pathCreator,
                                           final FaultPolicy faultPolicy) {
            super(config, pathCreator);
            this.faultPolicy = faultPolicy;
        }

        @Override
        public FileStore getFileStore(final String fileStoreName) {
            return wrapped.computeIfAbsent(
                    fileStoreName,
                    name -> new FaultInjectingFileStore(super.getFileStore(name), faultPolicy));
        }
    }

    // -------------------------------------------------------------------------
}
