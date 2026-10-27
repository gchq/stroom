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

package stroom.ai.impl.tools.sample;

import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.xml.converter.ds3.ConfigFilter;
import stroom.pipeline.xml.converter.ds3.DS3Parser;
import stroom.pipeline.xml.converter.ds3.RootFactory;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.ErrorType;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.xml.SAXParserFactoryFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.xml.parsers.SAXParserFactory;

/// A Data Splitter run by Stroom's own parser (the DSParser element's [DS3Parser]) on sample text: the records it
/// writes and the errors it reports. [SplitterDryRun] re-implements the subset of Data Splitter the generator
/// writes, and differs from Stroom where it hasn't followed it (seen in testing: records over the read buffer lost
/// in processing, and cells Stroom trims kept untrimmed); this is what processing will do. The converter is read
/// without the schema check the pipeline also makes: the tools write it, and save_text_converter checks it.
public final class StroomSplitterRun {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomSplitterRun.class);

    /// How long a run may take: Stroom's regexes can't be stopped part-way, so a run that takes longer is left
    /// to finish on its own (a daemon thread) and reported as too slow.
    public static final Duration TIME_LIMIT = Duration.ofSeconds(20);
    /// The most errors kept.
    public static final int MAX_ERRORS = 20;
    /// The name a record's data element with no name (a cell past its heading) is kept under.
    public static final String UNNAMED = "(no name)";

    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    private static final ElementId ELEMENT_ID = new ElementId("dsParser");
    private static final ExecutorService RUNNER = Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, "stroom-splitter-run");
        thread.setDaemon(true);
        return thread;
    });

    private StroomSplitterRun() {
        // Static only.
    }

    /// @param converter The Data Splitter's XML.
    /// @param text      The text to parse.
    /// @return What Stroom's parser made of it.
    public static Result run(final String converter, final String text) {
        return run(converter, text, TIME_LIMIT);
    }

    /// @param converter The Data Splitter's XML.
    /// @param text      The text to parse.
    /// @param limit     How long it may take.
    /// @return What Stroom's parser made of it; a converter Stroom can't read, or a run over the limit, as an error
    /// with no records.
    public static Result run(final String converter, final String text, final Duration limit) {
        final Future<Result> future = RUNNER.submit(() -> parse(converter, text));
        try {
            return future.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            future.cancel(true);
            return Result.failed("Stroom's parser took over " + limit.toSeconds() + " s on the sample: a pattern "
                                 + "backtracks too much, so processing would be as slow");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return Result.failed("Interrupted while Stroom's parser ran");
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause() == null
                    ? e
                    : e.getCause();
            LOGGER.debug(() -> LogUtil.message("Stroom's parser failed: {}", cause.getMessage()), cause);
            return Result.failed("Stroom couldn't run the converter: " + cause.getMessage());
        }
    }

    private static Result parse(final String converter, final String text) throws Exception {
        final RootFactory factory = new RootFactory();
        final XMLReader configReader = PARSER_FACTORY.newSAXParser().getXMLReader();
        configReader.setContentHandler(new ConfigFilter(factory));
        configReader.parse(new InputSource(new StringReader(converter)));
        factory.compile();

        final Errors errors = new Errors();
        final Records records = new Records();
        final DS3Parser parser = new DS3Parser(factory.newInstance(new VarMap()), RootFactory.MIN_BUFFER_SIZE,
                factory.getBufferSize());
        parser.setContentHandler(records);
        parser.setErrorHandler(new ErrorHandlerAdaptor(ELEMENT_ID, new DefaultLocationFactory(), errors));
        parser.parse(new InputSource(new StringReader(text)));
        return new Result(records.records, errors.messages, errors.total);
    }


    // --------------------------------------------------------------------------------


    /// What Stroom's parser made of the text.
    ///
    /// @param records The records, each its data elements' names to values (the first, when a name is given
    ///                again; nested data by its own name), in order.
    /// @param errors  The errors and warnings it reported (at most [#MAX_ERRORS]), each with its line and column.
    /// @param total   How many it reported in all.
    public record Result(List<Map<String, String>> records, List<String> errors, int total) {

        /// @param message Why there are no records.
        /// @return A result of no records, with the message as its error.
        static Result failed(final String message) {
            return new Result(List.of(), List.of(message), 1);
        }

        /// @return Whether it reported nothing.
        public boolean clean() {
            return total == 0;
        }
    }

    // The records the parser writes, in records:2.
    private static final class Records extends DefaultHandler {

        private final List<Map<String, String>> records = new ArrayList<>();
        private Map<String, String> current;

        @Override
        public void startElement(final String uri,
                                 final String localName,
                                 final String qName,
                                 final Attributes attributes) {
            final String name = localName == null || localName.isEmpty()
                    ? qName
                    : localName;
            if ("record".equals(name)) {
                current = new LinkedHashMap<>();
            } else if ("data".equals(name) && current != null) {
                final String dataName = attribute(attributes, "name");
                // A data element with no name (a cell past the heading) is kept as Stroom writes it: unnamed.
                final String value = attribute(attributes, "value");
                current.putIfAbsent(dataName == null
                        ? UNNAMED
                        : dataName, value == null
                        ? ""
                        : value);
            }
        }

        // By index: Stroom's DataAttributes fails on a name it doesn't hold (an empty value has no value
        // attribute) rather than returning null.
        private static String attribute(final Attributes attributes, final String name) {
            for (int i = 0; i < attributes.getLength(); i++) {
                final String local = attributes.getLocalName(i);
                if (name.equals(local == null || local.isEmpty()
                        ? attributes.getQName(i)
                        : local)) {
                    return attributes.getValue(i);
                }
            }
            return null;
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            final String name = localName == null || localName.isEmpty()
                    ? qName
                    : localName;
            if ("record".equals(name) && current != null) {
                records.add(current);
                current = null;
            }
        }
    }

    // The errors and warnings the parser reports.
    private static final class Errors implements ErrorReceiver {

        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        private int total;

        @Override
        public void log(final Severity severity,
                        final Location location,
                        final ElementId elementId,
                        final String message,
                        final ErrorType errorType,
                        final Throwable e) {
            if (severity == null || severity.lessThan(Severity.WARNING)) {
                return;
            }
            total++;
            if (messages.size() < MAX_ERRORS) {
                messages.add((location == null
                        ? ""
                        : "line " + location.getLineNo() + ", column " + location.getColNo() + ": ")
                             + severity.getDisplayValue() + ": " + message);
            }
        }
    }
}
