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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.xml.transform.Templates;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

/**
 * Isolates the per-transform overhead that makes <code>splitCount=1</code> slower than batching,
 * and measures how much of it reusing Saxon machinery can recover.
 * <p>
 * This deliberately bypasses the Stroom pipeline. SAX events are pushed straight into the transform
 * rather than parsed from a file, and the result is discarded, so what is left is Saxon's cost for
 * "start a transform, feed it one record's worth of events, finish it" versus doing the same work
 * in bulk.
 * <p>
 * Every method processes exactly {@link #RECORDS_PER_OP} records, so the scores are directly
 * comparable:
 * <ul>
 *     <li>{@link #newTransformerPerRecord} is what {@code XsltFilter} does today at
 *     {@code splitCount=1} — a fresh {@code Transformer} and {@code TransformerHandler} per
 *     record;</li>
 *     <li>{@link #reuseTransformerPerRecord} keeps one {@code Transformer} and creates only the
 *     handler per record;</li>
 *     <li>{@link #reuseTransformerWithResetPerRecord} adds the {@code reset()} that JAXP suggests
 *     between uses, to price it;</li>
 *     <li>{@link #singleTransformForAllRecords} is the batched control, i.e. what a large split
 *     count achieves.</li>
 * </ul>
 * Run with:
 * <pre>
 * ./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="SaxonReuseBenchmark -prof gc"
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = "-Xmx4g")
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class SaxonReuseBenchmark {

    /**
     * Enough records that per-operation JMH overhead is irrelevant, small enough that the batched
     * case does not build an enormous tree.
     */
    private static final int RECORDS_PER_OP = 1_000;

    private static final String RECORDS_NS = "records:2";

    /**
     * A copy of the pipeline's stylesheet with the one <code>stroom:format-date</code> call removed.
     * Stroom's extension functions are registered on Stroom's own Saxon {@code Configuration}, so the
     * original will not compile here; removing the call also keeps date parsing cost out of a
     * measurement that is meant to be about Saxon's per-transform machinery.
     */
    private static final String PLAIN_XSLT_RESOURCE = "/stroom/pipeline/benchmark/records-to-events-plain.xsl";

    private Templates templates;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        final String xslt;
        try (final InputStream in = SaxonReuseBenchmark.class
                .getResourceAsStream(PLAIN_XSLT_RESOURCE)) {
            xslt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Saxon's own factory, matching what Stroom compiles the stylesheet with.
        final TransformerFactoryImpl factory = new TransformerFactoryImpl();
        templates = factory.newTemplates(new StreamSource(new StringReader(xslt)));

        // Fail fast if the stylesheet is not actually matching, which would make every number here
        // meaningless, and check that reusing the tree still produces identical output rather than
        // a fast wrong answer.
        final String plain = transformOneRecord(false);
        final String reused = transformOneRecord(true);
        if (plain.isEmpty()) {
            throw new IllegalStateException("The stylesheet produced no output; the benchmark would be vacuous");
        }
        if (!plain.equals(reused)) {
            throw new IllegalStateException(
                    "Reusing the tree changed the output.\n plain  = " + plain + "\n reused = " + reused);
        }
    }

    /**
     * Runs three records through one transformer and returns a description of the output, so that
     * the reusing and non-reusing paths can be compared. Three rather than one because reuse only
     * starts on the second document.
     */
    private String transformOneRecord(final boolean reuseTree) throws Exception {
        final RecordingHandler recorder = new RecordingHandler();
        final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
        if (reuseTree) {
            transformer.getUnderlyingController().setModel(new ReusableTinyTreeModel());
        }
        for (int i = 0; i < 3; i++) {
            final TransformerHandler handler = transformer.newTransformerHandler();
            handler.setResult(new SAXResult(recorder));
            pushDocument(handler, 1);
        }
        return recorder.sb.toString();
    }

    /**
     * What {@code XsltFilter} does today for every split document.
     */
    @Benchmark
    public void newTransformerPerRecord(final Blackhole blackhole) throws Exception {
        final CountingHandler counter = new CountingHandler();
        for (int i = 0; i < RECORDS_PER_OP; i++) {
            final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
            final TransformerHandler handler = transformer.newTransformerHandler();
            handler.setResult(new SAXResult(counter));
            pushDocument(handler, 1);
        }
        blackhole.consume(counter.elementCount);
    }

    /**
     * One {@code Transformer} for the whole stream; only the handler is per record.
     */
    @Benchmark
    public void reuseTransformerPerRecord(final Blackhole blackhole) throws Exception {
        final CountingHandler counter = new CountingHandler();
        final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
        for (int i = 0; i < RECORDS_PER_OP; i++) {
            final TransformerHandler handler = transformer.newTransformerHandler();
            handler.setResult(new SAXResult(counter));
            pushDocument(handler, 1);
        }
        blackhole.consume(counter.elementCount);
    }

    /**
     * As above but calling {@code reset()} between records, to price the safer contract.
     */
    @Benchmark
    public void reuseTransformerWithResetPerRecord(final Blackhole blackhole) throws Exception {
        final CountingHandler counter = new CountingHandler();
        final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
        for (int i = 0; i < RECORDS_PER_OP; i++) {
            transformer.reset();
            final TransformerHandler handler = transformer.newTransformerHandler();
            handler.setResult(new SAXResult(counter));
            pushDocument(handler, 1);
        }
        blackhole.consume(counter.elementCount);
    }

    /**
     * Reuses the {@code Transformer} and, via {@link ReusableTinyTreeModel}, the source tree too.
     * <p>
     * This is the experiment: Saxon's own {@code TinyBuilder.open()} already skips allocating when
     * its tree is non-null, so a custom {@code TreeModel} that hands back a builder holding on to
     * its tree makes a stream of records share one set of arrays.
     */
    @Benchmark
    public void reuseTransformerAndTreePerRecord(final Blackhole blackhole) throws Exception {
        final CountingHandler counter = new CountingHandler();
        final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
        transformer.getUnderlyingController().setModel(new ReusableTinyTreeModel());
        for (int i = 0; i < RECORDS_PER_OP; i++) {
            final TransformerHandler handler = transformer.newTransformerHandler();
            handler.setResult(new SAXResult(counter));
            pushDocument(handler, 1);
        }
        blackhole.consume(counter.elementCount);
    }

    /**
     * The batched control: all the records in one transform, as a large split count achieves.
     */
    @Benchmark
    public void singleTransformForAllRecords(final Blackhole blackhole) throws Exception {
        final CountingHandler counter = new CountingHandler();
        final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
        final TransformerHandler handler = transformer.newTransformerHandler();
        handler.setResult(new SAXResult(counter));
        pushDocument(handler, RECORDS_PER_OP);
        blackhole.consume(counter.elementCount);
    }

    /**
     * Emits the SAX events for a <code>records:2</code> document holding <code>recordCount</code>
     * records, matching the shape the {@code SplitFilter} produces.
     */
    private static void pushDocument(final ContentHandler handler, final int recordCount)
            throws SAXException {
        handler.startDocument();
        handler.startPrefixMapping("", RECORDS_NS);

        final AttributesImpl rootAtts = new AttributesImpl();
        rootAtts.addAttribute("", "version", "version", "CDATA", "2.0");
        handler.startElement(RECORDS_NS, "records", "records", rootAtts);

        for (int i = 0; i < recordCount; i++) {
            handler.startElement(RECORDS_NS, "record", "record", new AttributesImpl());
            data(handler, "Date", "01/01/2010");
            data(handler, "Time", "00:00:00");
            data(handler, "FileNo", "1");
            data(handler, "LineNo", Integer.toString(i + 1));
            data(handler, "User", "user" + (i + 1));
            data(handler, "Message", "Some message " + (i + 1));
            handler.endElement(RECORDS_NS, "record", "record");
        }

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

    /**
     * Discards the result but counts elements, so the transform cannot be optimised away and the
     * setup can confirm the stylesheet actually matched.
     */
    private static final class CountingHandler extends DefaultHandler {

        private int elementCount;

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                                 final org.xml.sax.Attributes attributes) {
            elementCount++;
        }
    }

    /**
     * Records element names, attributes and text so two runs can be compared exactly. Only used by
     * the setup check, never on a measured path.
     */
    private static final class RecordingHandler extends DefaultHandler {

        private final StringBuilder sb = new StringBuilder();

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                                 final org.xml.sax.Attributes attributes) {
            sb.append('<').append(localName);
            for (int i = 0; i < attributes.getLength(); i++) {
                sb.append(' ').append(attributes.getLocalName(i)).append('=').append(attributes.getValue(i));
            }
            sb.append('>');
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) {
            sb.append(ch, start, length);
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            sb.append("</").append(localName).append('>');
        }
    }
}
