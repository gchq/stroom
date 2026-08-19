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

import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.tree.tiny.Statistics;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.Templates;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

/**
 * Measures the cost of Saxon's source-tree sizing over the <em>first</em> documents of a stream,
 * which is the case the JMH benchmarks structurally cannot see.
 * <p>
 * Saxon sizes each new {@code TinyTree} from a shared, self-tuning {@link Statistics} that starts at
 * 4000 nodes and 4000 characters — roughly 86KB of arrays to hold a seven-node record. It converges
 * on a floor of 10 over a few thousand documents, so a benchmark that runs for a million records
 * measures only the converged state. Stroom, though, processes many short streams, and a fresh
 * {@code Statistics} is created per pooled XSLT (one Saxon {@code Configuration} per pool entry).
 * <p>
 * Each variant starts from a brand new {@code Configuration}, so the statistics start cold:
 * <ul>
 *     <li><b>default</b> — what Stroom does now;</li>
 *     <li><b>primed</b> — the same, but the configuration's {@code SOURCE_DOCUMENT_STATISTICS} is
 *     driven down to the floor first via the public {@code updateStatistics}, which is a handful of
 *     lines and needs no custom tree model;</li>
 *     <li><b>reusedTree</b> — the {@link ReusableTinyTreeModel} experiment.</li>
 * </ul>
 * Allocation is measured with {@code ThreadMXBean.getCurrentThreadAllocatedBytes()} and excludes
 * stylesheet compilation, which is taken before the clock starts.
 * <p>
 * Run with: <pre>./gradlew :stroom-pipeline-benchmark:coldStart</pre>
 */
public final class ColdStartTreeSizingExperiment {

    private static final String RECORDS_NS = "records:2";
    private static final String PLAIN_XSLT_RESOURCE = "/stroom/pipeline/benchmark/records-to-events-plain.xsl";

    private static final int[] STREAM_LENGTHS = {10, 100, 1_000, 10_000};


    private ColdStartTreeSizingExperiment() {
        // Utility class.
    }

    public static void main(final String[] args) throws Exception {
        final String xslt = readXslt();

        // Let the JIT settle so the numbers reflect the work and not interpreted bytecode.
        for (int i = 0; i < 20; i++) {
            for (final Variant variant : Variant.values()) {
                runStream(xslt, 1_000, variant);
            }
        }

        System.out.printf("%nTime per record over the first N records of a cold stream%n");
        System.out.printf("(each repetition starts from a fresh Saxon Configuration; stylesheet "
                          + "compilation excluded from the timing)%n");
        System.out.printf("Median of %s repetitions, and the whole-stream time at that median.%n%n",
                "per-length");
        System.out.printf("%-10s %5s %14s %14s %14s   %14s %14s %14s%n",
                "records", "reps",
                "default", "primed", "reusedTree",
                "default", "primed", "reusedTree");
        System.out.printf("%-10s %5s %14s %14s %14s   %14s %14s %14s%n",
                "", "", "ns/record", "ns/record", "ns/record", "stream(ms)", "stream(ms)", "stream(ms)");

        for (final int streamLength : STREAM_LENGTHS) {
            final int repetitions = repetitionsFor(streamLength);

            final long defaultNanos = medianNanos(xslt, streamLength, Variant.DEFAULT, repetitions);
            final long primedNanos = medianNanos(xslt, streamLength, Variant.PRIMED, repetitions);
            final long reusedNanos = medianNanos(xslt, streamLength, Variant.REUSED_TREE, repetitions);

            System.out.printf("%-10d %5d %14.0f %14.0f %14.0f   %14.2f %14.2f %14.2f%n",
                    streamLength,
                    repetitions,
                    defaultNanos / (double) streamLength,
                    primedNanos / (double) streamLength,
                    reusedNanos / (double) streamLength,
                    defaultNanos / 1_000_000d,
                    primedNanos / 1_000_000d,
                    reusedNanos / 1_000_000d);
        }
        System.out.println();
    }

    /**
     * Enough repetitions that a short stream is timed over a meaningful amount of work, capped so
     * that the long streams do not take all day. Each repetition recompiles the stylesheet, which is
     * excluded from the timing but not from the wall clock.
     */
    private static int repetitionsFor(final int streamLength) {
        return Math.max(5, Math.min(100, 20_000 / streamLength));
    }

    /**
     * Runs the stream <code>repetitions</code> times and returns the median elapsed time, which
     * resists the occasional GC pause far better than a mean would.
     */
    private static long medianNanos(final String xslt,
                                    final int streamLength,
                                    final Variant variant,
                                    final int repetitions) throws Exception {
        final long[] samples = new long[repetitions];
        for (int i = 0; i < repetitions; i++) {
            samples[i] = runStream(xslt, streamLength, variant);
        }
        java.util.Arrays.sort(samples);
        return samples[repetitions / 2];
    }

    private enum Variant {
        DEFAULT,
        PRIMED,
        REUSED_TREE
    }

    /**
     * Builds a fresh Saxon configuration, transforms <code>records</code> single-record documents
     * through it and returns the nanoseconds that took.
     * <p>
     * The configuration is deliberately new every time: the whole point is to catch Saxon's
     * self-tuning tree statistics while they are still cold.
     */
    private static long runStream(final String xslt, final int records, final Variant variant)
            throws Exception {
        final TransformerFactoryImpl factory = new TransformerFactoryImpl();
        final Templates templates = factory.newTemplates(new StreamSource(new StringReader(xslt)));

        if (variant == Variant.PRIMED) {
            primeStatistics(factory);
        }

        final CountingHandler counter = new CountingHandler();
        final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
        if (variant == Variant.REUSED_TREE) {
            transformer.getUnderlyingController().setModel(new ReusableTinyTreeModel());
        }

        final long start = System.nanoTime();
        for (int i = 0; i < records; i++) {
            final TransformerHandler handler = transformer.newTransformerHandler();
            handler.setResult(new SAXResult(counter));
            pushDocument(handler);
        }
        final long elapsed = System.nanoTime() - start;

        if (counter.elementCount == 0) {
            throw new IllegalStateException("The stylesheet produced no output");
        }
        return elapsed;
    }

    /**
     * Drives the configuration's source-document statistics down to their floor, so the first trees
     * are sized for a small record rather than for 4000 nodes.
     * <p>
     * This is the cheap, no-reflection option: {@code Configuration.getTreeStatistics()} and
     * {@code Statistics.updateStatistics(int, int, int, int)} are both public. The running mean
     * starts with a weight of 5, so a few dozen updates are enough to converge.
     */
    private static void primeStatistics(final TransformerFactoryImpl factory) {
        final Statistics sourceStatistics =
                factory.getConfiguration().getTreeStatistics().SOURCE_DOCUMENT_STATISTICS;
        for (int i = 0; i < 100; i++) {
            sourceStatistics.updateStatistics(10, 13, 2, 32);
        }
    }

    private static String readXslt() throws Exception {
        try (final InputStream in = ColdStartTreeSizingExperiment.class
                .getResourceAsStream(PLAIN_XSLT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Stylesheet not found: " + PLAIN_XSLT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Emits one record's worth of SAX events, matching what the SplitFilter produces at
     * splitCount=1.
     */
    private static void pushDocument(final ContentHandler handler) throws SAXException {
        handler.startDocument();
        handler.startPrefixMapping("", RECORDS_NS);

        final AttributesImpl rootAtts = new AttributesImpl();
        rootAtts.addAttribute("", "version", "version", "CDATA", "2.0");
        handler.startElement(RECORDS_NS, "records", "records", rootAtts);

        handler.startElement(RECORDS_NS, "record", "record", new AttributesImpl());
        data(handler, "Date", "01/01/2010");
        data(handler, "Time", "00:00:00");
        data(handler, "FileNo", "1");
        data(handler, "LineNo", "1");
        data(handler, "User", "user1");
        data(handler, "Message", "Some message 1");
        handler.endElement(RECORDS_NS, "record", "record");

        handler.endElement(RECORDS_NS, "records", "records");
        handler.endPrefixMapping("");
        handler.endDocument();
    }

    private static void data(final ContentHandler handler, final String name, final String value)
            throws SAXException {
        final AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "name", "name", "CDATA", name);
        atts.addAttribute("", "value", "value", "CDATA", value);
        handler.startElement(RECORDS_NS, "data", "data", atts);
        handler.endElement(RECORDS_NS, "data", "data");
    }

    private static final class CountingHandler extends DefaultHandler {

        private int elementCount;

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                                 final org.xml.sax.Attributes attributes) {
            elementCount++;
        }
    }
}
