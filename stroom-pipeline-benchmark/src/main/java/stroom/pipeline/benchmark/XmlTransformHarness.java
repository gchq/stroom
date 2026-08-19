/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.benchmark;

import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.xslt.XsltStore;
import stroom.task.api.SimpleTaskContext;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds and runs the pipeline
 * <pre>XMLParser -&gt; SplitFilter -&gt; XSLTFilter -&gt; XMLWriter -&gt; FileAppender</pre>
 * over a <code>records:2</code> input file, producing <code>event-logging:3</code> output.
 * <p>
 * The point of the harness is to make the split count the only thing that varies. One injector, one
 * XSLT document and one compiled stylesheet pool are shared across every run; only the
 * <code>splitCount</code> property and the output path change.
 * <p>
 * This deliberately avoids JUnit and {@code AbstractProcessIntegrationTest} so that a forked JMH JVM
 * can build it directly. Not thread safe; give each thread its own harness.
 */
public class XmlTransformHarness implements AutoCloseable {

    public static final String PIPELINE_RESOURCE = "/stroom/pipeline/benchmark/XMLTransformerSplit.Pipeline.json";
    public static final String XSLT_RESOURCE = "/stroom/pipeline/benchmark/records-to-events.xsl";

    /**
     * The same pipeline with the {@code XSLTFilter} removed, i.e.
     * <pre>XMLParser -&gt; SplitFilter -&gt; XMLWriter -&gt; FileAppender</pre>
     * Used to attribute the cost of a small split count: whatever penalty remains without the XSLT
     * element belongs to the surrounding pipeline rather than to Saxon.
     */
    public static final String PIPELINE_RESOURCE_NO_XSLT =
            "/stroom/pipeline/benchmark/XMLTransformerSplitNoXslt.Pipeline.json";

    /**
     * Split on the children of the <code>records</code> root, so that a group is always a whole
     * number of records. This is the only split depth that makes sense for this input, so it is
     * fixed here rather than being another dimension to vary.
     */
    public static final int SPLIT_DEPTH = 1;

    /**
     * Element ids as they appear in {@link #PIPELINE_RESOURCE}.
     */
    private static final String SPLIT_FILTER = "splitFilter";
    private static final String TRANSLATION_FILTER = "translationFilter";
    private static final String FILE_APPENDER = "fileAppender";

    private final Path baseDir;
    private final boolean ownsBaseDir;
    private final Injector injector;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    /**
     * One pipeline document per distinct configuration. Reused across runs so that the
     * {@link PipelineDataCache} entry and the compiled XSLT are warm, leaving the transform itself
     * as what a run measures.
     */
    private final Map<PipelineKey, DocRef> pipelineRefs = new HashMap<>();

    @Inject
    private Provider<PipelineFactory> pipelineFactoryProvider;
    @Inject
    private Provider<ErrorReceiverProxy> errorReceiverProvider;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private PipelineDataCache pipelineDataCache;

    private DocRef xsltRef;

    /**
     * Creates a harness rooted at a fresh temporary directory that is deleted on {@link #close()}.
     */
    public XmlTransformHarness() throws IOException {
        this(Files.createTempDirectory("stroom-pipeline-benchmark"), true);
    }

    /**
     * Creates a harness rooted at <code>baseDir</code>, which the caller owns.
     */
    public XmlTransformHarness(final Path baseDir) throws IOException {
        this(baseDir, false);
    }

    private XmlTransformHarness(final Path baseDir, final boolean ownsBaseDir) throws IOException {
        this.baseDir = baseDir;
        this.ownsBaseDir = ownsBaseDir;

        final Path homeDir = Files.createDirectories(baseDir.resolve("home"));
        final Path tempDir = Files.createDirectories(baseDir.resolve("temp"));

        this.injector = Guice.createInjector(new PipelineBenchmarkModule(homeDir, tempDir));
        this.injector.injectMembers(this);
        this.pipelineScopeRunnable = injector.getInstance(PipelineScopeRunnable.class);

        createXslt();
    }

    private void createXslt() {
        xsltRef = xsltStore.createDocument("Records to Events");
        final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef)
                .copy()
                .data(readResource(XSLT_RESOURCE))
                .build();
        xsltStore.writeDocument(xsltDoc);
    }

    /**
     * Returns (creating on first use) the pipeline document configured for the given split count.
     * <p>
     * A split count of 0 means "do not split" — every record ends up in a single output document.
     */
    private DocRef pipelineRefFor(final int splitCount, final Path outputFile, final boolean withXslt) {
        return pipelineRefs.computeIfAbsent(new PipelineKey(splitCount, outputFile, withXslt), key -> {
            final DocRef docRef = pipelineStore.createDocument("Split " + key.splitCount());
            final PipelineDoc created = pipelineStore.readDocument(docRef);

            final PipelineData basePipelineData = JsonUtil.readValue(
                    readResource(key.withXslt()
                            ? PIPELINE_RESOURCE
                            : PIPELINE_RESOURCE_NO_XSLT),
                    PipelineData.class);
            final PipelineDataBuilder builder = new PipelineDataBuilder(basePipelineData);
            if (key.withXslt()) {
                builder.addProperty(PipelineDataUtil.createProperty(TRANSLATION_FILTER, "xslt", xsltRef));
            }
            builder.addProperty(new PipelineProperty(
                    SPLIT_FILTER, "splitDepth", new PipelinePropertyValue(SPLIT_DEPTH)));
            builder.addProperty(new PipelineProperty(
                    SPLIT_FILTER, "splitCount", new PipelinePropertyValue(key.splitCount())));
            builder.addProperty(PipelineDataUtil.createProperty(
                    FILE_APPENDER, "outputPaths", FileUtil.getCanonicalPath(key.outputFile())));

            pipelineStore.writeDocument(created.copy().pipelineData(builder.build()).build());
            return docRef;
        });
    }

    /**
     * Identifies a pipeline document. The output path is part of the key because it is baked into
     * the document as the {@code FileAppender}'s {@code outputPaths}, so two runs at the same split
     * count writing to different files need different documents.
     */
    private record PipelineKey(int splitCount, Path outputFile, boolean withXslt) {

    }

    /**
     * Runs the pipeline once.
     * <p>
     * Any pre-existing output is removed first, because {@code FileAppender} refuses to write to a
     * path where either the output file or its <code>.lock</code> sibling already exists.
     *
     * @param input      the <code>records:2</code> source file.
     * @param splitCount records per output document; 0 means do not split.
     * @param outputFile where the {@code FileAppender} writes.
     * @return how long the pipeline took, and any errors it reported.
     */
    public RunResult run(final Path input, final int splitCount, final Path outputFile) {
        return run(input, splitCount, outputFile, true);
    }

    /**
     * As {@link #run(Path, int, Path)}, but able to run the pipeline without its {@code XSLTFilter}
     * so that the cost of a small split count can be attributed to Saxon or to the pipeline around it.
     */
    public RunResult run(final Path input,
                         final int splitCount,
                         final Path outputFile,
                         final boolean withXslt) {
        try {
            Files.createDirectories(outputFile.getParent());
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(outputFile.resolveSibling(outputFile.getFileName() + ".lock"));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        // Resolve the pipeline outside the timed region so that first-use document creation is not
        // charged to the run.
        final DocRef pipelineRef = pipelineRefFor(splitCount, outputFile, withXslt);

        final RunResult[] result = new RunResult[1];
        pipelineScopeRunnable.scopeRunnable(() -> {
            try (final InputStream inputStream = Files.newInputStream(input)) {
                final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
                errorReceiverProvider.get().setErrorReceiver(errorReceiver);

                final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
                final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                final Pipeline pipeline = pipelineFactoryProvider.get()
                        .create(pipelineData, new SimpleTaskContext());

                final long start = System.nanoTime();
                pipeline.startProcessing();
                pipeline.process(inputStream, StandardCharsets.UTF_8.name());
                pipeline.endProcessing();
                final long durationNanos = System.nanoTime() - start;

                result[0] = new RunResult(splitCount, durationNanos, errorReceiver);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return result[0];
    }

    public Path getBaseDir() {
        return baseDir;
    }

    private static String readResource(final String resource) {
        try (final InputStream in = XmlTransformHarness.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (ownsBaseDir) {
            FileUtil.deleteDir(baseDir);
        }
    }

    /**
     * The outcome of a single pipeline run.
     */
    public record RunResult(int splitCount, long durationNanos, LoggingErrorReceiver errorReceiver) {

        public boolean isAllOk() {
            return errorReceiver.isAllOk();
        }

        public double durationMillis() {
            return durationNanos / 1_000_000d;
        }
    }
}
