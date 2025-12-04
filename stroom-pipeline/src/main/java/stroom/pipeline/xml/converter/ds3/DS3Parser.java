/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.xml.NamespaceConstants;
import stroom.pipeline.xml.converter.AbstractParser;
import stroom.pipeline.xml.converter.ds3.GroupFactory.MatchOrder;
import stroom.pipeline.xml.converter.ds3.NodeFactory.NodeType;
import stroom.util.CharBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

/**
 * Converter for a flat file to a SAX stream.
 */
public class DS3Parser extends AbstractParser {

    private static final String XSI_SCHEMA_LOCATION = "xsi:schemaLocation";
    private static final String SCHEMA_LOCATION = "schemaLocation";
    private static final String XMLNS_XSI = "xmlns:xsi";
    private static final String XML_TYPE_STRING = "string";
    private static final String XMLNS = "xmlns";
    private static final String XMLSCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XSI = "xsi";
    private static final String EMPTY_STRING = "";

    private static final String XML_ELEMENT_ROOT = "records";
    private static final String XML_ELEMENT_RECORD = "record";
    private static final String XML_ELEMENT_DATA = "data";
    private static final String XML_ATTRIBUTE_VERSION = "version";

    private static final String NAMESPACE = NamespaceConstants.RECORDS;
    private static final String LOCATION = " file://records-v2.0.xsd";
    private static final String VERSION = "2.0";

    private static final Attributes EMPTY_ATTS = new AttributesImpl();
    private static final AttributesImpl ROOT_ATTS = new AttributesImpl();
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DS3Parser.class);
    private static final int RECOVERY_MODE = -99;

    static {
        ROOT_ATTS.addAttribute(EMPTY_STRING, XMLNS, XMLNS, XML_TYPE_STRING, NAMESPACE);
        ROOT_ATTS.addAttribute(EMPTY_STRING, XSI, XMLNS_XSI, XML_TYPE_STRING, XMLSCHEMA_INSTANCE);
        ROOT_ATTS.addAttribute(XMLSCHEMA_INSTANCE, SCHEMA_LOCATION, XSI_SCHEMA_LOCATION, XML_TYPE_STRING,
                NAMESPACE + LOCATION);
        ROOT_ATTS.addAttribute(EMPTY_STRING, XML_ATTRIBUTE_VERSION, XML_ATTRIBUTE_VERSION, XML_TYPE_STRING, VERSION);
    }

    private final DataAttributes dataAttributes = new DataAttributes();
    private final Root root;
    private final int minBuffer;
    private final int maxBuffer;
    private final CharBuffer messageBuffer = new CharBuffer();
    private boolean inRecord;
    private DS3Reader reader;
    private ScheduledExecutorService profilingExecutor;
    private ErrorHandlerAdaptor errorHandlerAdaptor;

    public DS3Parser(final Root root, final int minBuffer, final int maxBuffer) {
        this.root = root;
        this.minBuffer = minBuffer;
        this.maxBuffer = maxBuffer;
    }

    /**
     * Parses a flat file and produces SAX events according to the feed
     * definition.
     *
     * @param input The flat input source to process.
     * @throws IOException  Could be thrown reading the stream.
     * @throws SAXException Not be thrown as we are not reading an XML file just using
     *                      the XMLReader interface.
     * @see org.xml.sax.XMLReader#parse(org.xml.sax.InputSource)
     */
    @Override
    public void parse(final InputSource input) throws IOException, SAXException {
        // Reset some variables in case this parser is being reused.
        inRecord = false;
        root.clear();
        profilingExecutor = null;
        messageBuffer.clear();

        // Get the error handler adaptor if we can so that other message
        // severities can be reported.
        errorHandlerAdaptor = (ErrorHandlerAdaptor) getErrorHandler();

        // Start profiling service if we need to.
        startProfiling();

        // Add a location reader so that we can inform the pipeline what the
        // current read location is.
        if (reader == null) {
            reader = new DS3Reader(input.getCharacterStream(), minBuffer, maxBuffer);
        } else {
            // Reuse the reader if we can as it saves discarding a char buffer.
            reader.setReader(input.getCharacterStream());
        }

        // Set the locator so that the filters know what the current document
        // location is.
        getContentHandler().setDocumentLocator(reader);

        // Start creating the XML output.
        startDocument();

        // Fill up the buffer.
        reader.fillBuffer();

        // Loop round the top level expressions as many times as necessary to
        // match as much content as possible.
        process(root,
                reader,
                null,
                0,
                0,
                MatchOrder.SEQUENCE,
                root.isIgnoreErrors());

        // Make sure we consumed all of the input (ignoring trailing
        // whitespace).
        if (!root.isIgnoreErrors() && (!reader.isEof() || !reader.isBlank())) {
            log(Severity.ERROR, "Top level expressions failed to match all of the content.");
        }

        // Close the reader.
        reader.close();

        // Finish creating the XML output.
        endDocument();

        // Stop profiling service.
        stopProfiling();
    }

    private void process(final Node parent,
                         final Buffer buffer,
                         final Match parentMatch,
                         final int parentMatchCount,
                         final int level,
                         final MatchOrder matchOrder,
                         final boolean ignoreErrors) throws IOException, SAXException {
        int advance = 1;
        boolean firstPass = true;
        int matchCount = 0;
        Expression expression = null;

        // Reset expression match counts.
        for (final Node node : parent.getChildNodes()) {
            if (node.isExpression()) {
                ((Expression) node).resetMatchCount();
            }
        }

        // Now try and match content with expressions and output data.
        while (advance > 0) {
            advance = 0;

            for (final Node node : parent.getChildNodes()) {
                if (node.isExpression()) {
                    // If the node is an expression and we haven't matched an
                    // expression yet (i.e. advance <= 0) then try and match
                    // this expression. If we are in recovery mode then don't
                    // try and match any other expressions until the next pass.
                    if (advance <= 0 && advance != RECOVERY_MODE) {
                        expression = (Expression) node;
                        advance = processExpression(
                                expression,
                                buffer,
                                parentMatchCount,
                                matchCount,
                                level,
                                matchOrder,
                                ignoreErrors);
                        // If we found a match then increase the match count.
                        if (advance > 0) {
                            matchCount++;
                        }
                    }
                } else if (firstPass) {
                    if (node.getNodeType() == NodeType.GROUP) {
                        storeData((Group) node, buffer, parentMatch, parentMatchCount);
                        processGroup((Group) node, buffer, parentMatch, parentMatchCount, level);
                    } else if (node.getNodeType() == NodeType.DATA) {
                        storeData((Data) node, buffer, parentMatch, parentMatchCount);
                        processData(
                                (Data) node,
                                buffer,
                                parentMatch,
                                parentMatchCount,
                                level,
                                matchOrder,
                                ignoreErrors);
                    } else if (node.getNodeType() == NodeType.VAR) {
                        storeData((Var) node, buffer, parentMatch, parentMatchCount);
                    }
                }
            }

            if (level == 0) {
                // Change the reader location if this is a top level match.
                if (advance == RECOVERY_MODE) {
                    // If we are in recovery mode then set advance to 1 so that
                    // we keep trying to process.
                    advance = 1;
                }

                // Fill up the buffer.
                reader.fillBuffer();

                // End the record if needed.
                endRecord();

                // Change the reader location for error reporting etc.
                reader.changeLocation();
            }

            // Start profiling service if we need to in case it has been turned
            // on since execution start.
            startProfiling();

            firstPass = false;
        }
    }

    private int processExpression(final Expression expression,
                                  final Buffer buffer,
                                  final int parentMatchCount,
                                  final int matchCount,
                                  final int level,
                                  final MatchOrder matchOrder,
                                  final boolean ignoreErrors)
            throws IOException, SAXException {
        int start = -1;
        int end = -1;

        // We don't know if this is a match yet so plus one to the matches so far to
        // get the match number this would be if it matches
        final int potentialMatchNumber = parentMatchCount + 1;

        // Check that we haven't already exceeded the maximum match count for
        // this expression.
        if (expression.checkMaxMatch()
                && expression.checkOnlyMatch(potentialMatchNumber)) {
            // Set the input that this expression will use.
            expression.setInput(buffer);

            // Try to find a match.
            Match match = null;
            try {
                match = expression.match();
            } catch (final ComplexRegexException e) {
                handleComplexRegexException(e, expression);
            } catch (final RuntimeException e) {
                handleRuntimeException(e, expression);
            }

            if (match != null) {
                // Find the first and last char position that the matcher found.
                start = match.start();
                end = match.end();

                // Make sure the matcher matched from the first character if we
                // are matching in sequence.
                if (matchOrder == MatchOrder.SEQUENCE && !ignoreErrors && start != 0) {
                    messageBuffer.clear();
                    messageBuffer.append("Expression failed to match from the start of the content (matched at char ");
                    messageBuffer.append(start + 1); // make one based
                    messageBuffer.append(" [one based], unmatched content [");
                    appendBufferContents(messageBuffer, buffer.subSequence(0, start));
                    messageBuffer.append("]): ");
                    messageBuffer.append(expression.getDebugId());
                    log(Severity.ERROR, messageBuffer.toString());
                }

                // We might not want to move the character buffer to the end of
                // the match if advance has been specified. We are calculating
                // end outside of processing matches as we want to abort
                // processing if advance stops the buffer moving on.
                if (expression.getAdvance() != -1) {
                    try {
                        end = match.end(expression.getAdvance());
                    } catch (final IndexOutOfBoundsException e) {
                        messageBuffer.clear();
                        messageBuffer.append("Advance group number ");
                        messageBuffer.append(expression.getAdvance());
                        messageBuffer.append(" not found in expression: ");
                        messageBuffer.append(expression.getDebugId());
                        log(Severity.FATAL_ERROR, messageBuffer.toString());
                    }
                }

                // Make sure we matched some characters and are moving on.
                if (end > 0) {
                    // Make sure we haven't exhausted the reader.
                    if (level == 0 && !reader.isEof() && reader.length() == end) {
                        // We have exhausted the buffer so log an error and
                        // attempt to recover.
                        final int recoveryAdvance = end / 2;
                        end = RECOVERY_MODE;
                        attemptRecovery(expression, recoveryAdvance);

                    } else {
                        // Process sub nodes.
                        process(expression, buffer, match, matchCount, level + 1, matchOrder, ignoreErrors);

                        // Increment the match count.
                        expression.incrementMatchCount();

                        // If we are matching in sequence then we can just move
                        // the char stream on. If not then we need to eliminate
                        // the match from the char stream.
                        if (matchOrder == MatchOrder.SEQUENCE) {
                            // Move the char stream on.
                            buffer.move(end);
                        } else {
                            // Remove the matched section from the buffer.
                            buffer.remove(start, end);
                        }
                    }
                }
            }
        }

        // Make sure the expression matched the minimum number of times.
        if (end != RECOVERY_MODE && !expression.checkMinMatch()) {
            messageBuffer.clear();
            messageBuffer.append("Expression did not match the required number of times (match count: ");
            messageBuffer.append(expression.getMatchCount());
//            messageBuffer.append(" content: [");
//            appendBufferContents(messageBuffer, buffer);
//            messageBuffer.append("]");
            messageBuffer.append("): ");
            messageBuffer.append(expression.getDebugId());
            log(Severity.ERROR, messageBuffer.toString());
        }

        // Return the end match position.
        return end;
    }

    private void attemptRecovery(final Expression expression, final int recoveryAdvance) throws IOException {
        messageBuffer.clear();
        messageBuffer.append("Top level expression '");
        messageBuffer.append(expression.getDebugId());
        messageBuffer.append("' matched too much content and exhausted the buffer. Attempting recovery...");
        log(Severity.ERROR, messageBuffer.toString());

        // Attempt recovery.
        int start = 0;
        int end = reader.length();
        while (!reader.isEof() && reader.length() == end) {
            reader.move(recoveryAdvance);
            reader.fillBuffer();

            Match recoveryMatch = null;
            try {
                recoveryMatch = expression.match();
            } catch (final ComplexRegexException e) {
                handleComplexRegexException(e, expression);
            } catch (final RuntimeException e) {
                handleRuntimeException(e, expression);
            }

            if (recoveryMatch != null) {
                // Find the first and last char position that the matcher found.
                start = recoveryMatch.start();
                end = recoveryMatch.end();

                // We might not want to move the character buffer to the end of
                // the match if advance has been specified. We are calculating
                // end outside of processing matches as we want to abort
                // processing if advance stops the buffer moving on.
                if (expression.getAdvance() != -1) {
                    try {
                        end = recoveryMatch.end(expression.getAdvance());
                    } catch (final IndexOutOfBoundsException e) {
                        messageBuffer.clear();
                        messageBuffer.append("Advance group number ");
                        messageBuffer.append(expression.getAdvance());
                        messageBuffer.append(" not found in expression: ");
                        messageBuffer.append(expression.getDebugId());
                        log(Severity.FATAL_ERROR, messageBuffer.toString());
                    }
                }

                if (start > 0) {
                    // Move to the start of the match for the next pass to
                    // continue from here.
                    reader.move(start);
                } else if (end > 0 && end < reader.length()) {
                    // Move to the end of the match for the next pass to
                    // continue from here.
                    reader.move(end);
                }
            }
        }

        if (reader.isEof() && reader.length() == end) {
            messageBuffer.clear();
            messageBuffer.append("Top level expression '");
            messageBuffer.append(expression.getDebugId());
            messageBuffer.append("' matched too much content and reached the end of the stream without recovering");
            log(Severity.FATAL_ERROR, messageBuffer.toString());

            // Move to the end of the stream so no further matching is
            // attempted.
            reader.move(end);
        } else {
            reader.changeLocation();
            log(Severity.INFO, "Attempting recovery from this position");
        }
    }

    /**
     * Store data against a node for future retrieval by this node or by remote
     * reference from another node in the case of var elements.
     */
    private void storeData(final StoreNode node,
                           final Buffer buffer,
                           final Match parentMatch,
                           final int parentMatchCount) throws IOException, SAXException {
        final int[] referencedGroups = node.getAllReferencedGroups();

        // Store value for future use.
        if (referencedGroups != null && referencedGroups.length > 0) {
            if (parentMatchCount == 0) {
                node.clearStores();
            }

            if (parentMatch == null) {
                // Parent match will only be null if a <var> or <data> element
                // is used within <group>. In these cases only group 0 can be
                // used as there is no parent match and the whole content from
                // the group will be used.
                for (final int groupNo : referencedGroups) {
                    try {
                        if (groupNo == 0) {
                            // Use the whole parent buffer.
                            node.storeValue(groupNo, parentMatchCount, buffer);

                        } else {
                            node.storeValue(groupNo, parentMatchCount, null);

                            messageBuffer.clear();
                            messageBuffer.append("Group number ");
                            messageBuffer.append(groupNo);
                            messageBuffer.append(" not valid within parent group of: ");
                            messageBuffer.append(node.getDebugId());
                            log(Severity.FATAL_ERROR, messageBuffer.toString());
                        }
                    } catch (final SAXException e) {
                        log(Severity.FATAL_ERROR, e.getMessage());
                    }
                }
            } else {
                for (final int groupNo : referencedGroups) {
                    try {
                        // Filter the buffer with the parent expression.
                        final Buffer val = parentMatch.filter(buffer, groupNo);
                        node.storeValue(groupNo, parentMatchCount, val);

                    } catch (final SAXException e) {
                        log(Severity.FATAL_ERROR, e.getMessage());

                    } catch (final IndexOutOfBoundsException e) {
                        node.storeValue(groupNo, parentMatchCount, null);

                        messageBuffer.clear();
                        messageBuffer.append("Group number ");
                        messageBuffer.append(groupNo);
                        messageBuffer.append(" not found in parent expression of: ");
                        messageBuffer.append(node.getDebugId());
                        log(Severity.FATAL_ERROR, messageBuffer.toString());
                    }
                }
            }
        }
    }

    private void processGroup(final Group node,
                              final Buffer buffer,
                              final Match parentMatch,
                              final int parentMatchCount,
                              final int level) throws IOException, SAXException {
        // Pull back the stored value.
        Buffer subBuffer = node.lookupValue(parentMatchCount);
        if (subBuffer != null) {
            if (node.isReverse()) {
                // Create a buffer that will return characters in reverse.
                subBuffer = subBuffer.reverse();
            } else {
                // Create a copy of the buffer so that subsequent processing,
                // moves etc will not affect the original buffer.
                subBuffer = subBuffer.unsafeCopy();
            }

            // Process the sub buffer with sub expressions.
            process(node,
                    subBuffer,
                    null,
                    parentMatchCount,
                    level + 1,
                    node.getMatchOrder(),
                    node.isIgnoreErrors());

            // Make sure we consumed all of the sub buffer (ignoring trailing
            // whitespace).
            if (!node.isIgnoreErrors() && !subBuffer.isBlank()) {

                messageBuffer.clear();
                messageBuffer
                        .append("Expressions failed to match all of the content provided by group: ")
                        .append(node.getDebugId())
                        .append(" unmatched content: [");

                appendBufferContents(messageBuffer, subBuffer);

                messageBuffer.append("]");

                log(Severity.ERROR, messageBuffer.toString());
            }
        }
    }

    private void appendBufferContents(final CharBuffer messageBuffer,
                                      final Buffer buffer) {
        // Limit the amount of unmatched content we put in the msg in case
        // it is mahoosive.
        final int maxBuffLen = 200;
        final int tailLen = 10;
        final int headLen = maxBuffLen - tailLen;

        if (buffer.length() > maxBuffLen) {
            messageBuffer
                    .append(buffer.subSequence(0, headLen)
                            .toString()
                            .replace("\n", "\\n"))
                    .append("...TRUNCATED...")
                    .append(buffer.subSequence(buffer.length() - tailLen, tailLen)
                            .toString()
                            .replace("\n", "\\n"));
        } else {
            messageBuffer.append(buffer.unsafeCopy()
                    .toString()
                    .replace("\n", "\\n"));
        }
    }

    /**
     * Outputs a data element if required using the current match or another
     * stored match.
     */
    private void processData(final StoreNode node,
                             final Buffer buffer,
                             final Match parentMatch,
                             final int parentMatchCount,
                             final int level,
                             final MatchOrder matchOrder,
                             final boolean ignoreErrors)
            throws IOException, SAXException {
        boolean outputEndElement = false;
        if (node.getNodeType() == NodeType.DATA) {
            final Data data = (Data) node;

            // Output name and value if required.
            final String name = normaliseBuffer(data.lookupName(parentMatchCount));
            final String value = normaliseBuffer(data.lookupValue(parentMatchCount));

            // Start the record if we aren't already in one.
            startRecord();

            // Start a data element for this record.
            startData(name, value);

            // Record that we need to output end element.
            outputEndElement = true;
        }

        // Process the buffer with sub expressions.
        process(node,
                buffer,
                parentMatch,
                parentMatchCount,
                level + 1,
                matchOrder,
                ignoreErrors);
        // Output the end element for data if we outputted a start element.
        if (outputEndElement) {
            endData();
        }
    }

    private String normaliseBuffer(final Buffer buffer) {
        Buffer buf = buffer;
        if (buf != null) {
            buf = buf.trim();
            if (buf.length() > 0) {
                return buf.toString();
            }
        }

        return null;
    }

    private void startDocument() throws SAXException {
        getContentHandler().startDocument();
        getContentHandler().startPrefixMapping(EMPTY_STRING, NAMESPACE);
        getContentHandler().startPrefixMapping(XSI, XMLSCHEMA_INSTANCE);
        getContentHandler().startElement(NAMESPACE, XML_ELEMENT_ROOT, XML_ELEMENT_ROOT, ROOT_ATTS);
    }

    private void endDocument() throws SAXException {
        getContentHandler().endElement(NAMESPACE, XML_ELEMENT_ROOT, XML_ELEMENT_ROOT);
        getContentHandler().endDocument();
    }

    private void startRecord() throws SAXException {
        if (!inRecord) {
            inRecord = true;

            LOGGER.trace(() -> LogUtil.message("startRecord location: {}", reader.getCurrentLocationAsStart()));
            getContentHandler().startElement(NAMESPACE, XML_ELEMENT_RECORD, XML_ELEMENT_RECORD, EMPTY_ATTS);
        }
    }

    private void endRecord() throws SAXException {
        if (inRecord) {
            inRecord = false;

            LOGGER.trace(() -> LogUtil.message("endRecord location: {}", reader.getCurrentLocationAsEnd()));
            getContentHandler().endElement(NAMESPACE, XML_ELEMENT_RECORD, XML_ELEMENT_RECORD);
        }
    }

    private void startData(final String name, final String value) throws SAXException {
        dataAttributes.setData(name, value);

        getContentHandler().startElement(NAMESPACE, XML_ELEMENT_DATA, XML_ELEMENT_DATA, dataAttributes);
    }

    private void endData() throws SAXException {
        getContentHandler().endElement(NAMESPACE, XML_ELEMENT_DATA, XML_ELEMENT_DATA);
    }

    private void startProfiling() {
        if (LOGGER.isDebugEnabled() && profilingExecutor == null) {
            // If you switch on debugging in ds3 you can find out problem
            // regex's.
            final Runnable command = () -> {
                final ExecutionProfilerTopN top10 = new ExecutionProfilerTopN(root, 10);
                // No point logging if we have no executions
                final long totalExecutionTime = top10.getTopN().stream()
                        .mapToLong(ExecutionProfiler::getTotalExecutionTime)
                        .sum();

                if (!top10.getTopN().isEmpty() || totalExecutionTime > 0) {
                    final StringBuilder debugLine = new StringBuilder();
                    debugLine.append("process() - Top 10 executions : ");
                    for (final ExecutionProfiler ex : top10.getTopN()) {
                        debugLine.append("\n\t");
                        debugLine.append(ex.getExecutionString());
                        debugLine.append(" (");
                        debugLine.append(ModelStringUtil.formatCsv(ex.getTotalExecutionCount()));
                        debugLine.append(") ");
                        debugLine.append(ModelStringUtil.formatDurationString(ex.getTotalExecutionTime()));
                    }
                    LOGGER.debug(debugLine.toString());
                }
            };

            profilingExecutor = Executors.newSingleThreadScheduledExecutor();
            profilingExecutor.scheduleWithFixedDelay(command, 10, 10, TimeUnit.SECONDS);
        }
    }

    private void stopProfiling() {
        if (profilingExecutor != null) {
            profilingExecutor.shutdown();
            profilingExecutor = null;
        }
    }

    private void log(final Severity severity, final String message) {
        log(severity, message, null);
    }

    private void log(final Severity severity, final String message, final Throwable t) {
        errorHandlerAdaptor.log(severity, reader, message, t);
    }

    private void handleRuntimeException(final RuntimeException e, final Expression expression) {
        messageBuffer.clear();
        messageBuffer.append("Expression '")
                .append(expression.getDebugId())
                .append("' threw a '")
                .append(e.getClass().getSimpleName())
                .append("' while matching");
        final String message = messageBuffer.toString();
        log(Severity.FATAL_ERROR, message, e);
        // We should never get exceptions here so log stack trace.
        LOGGER.error(message, e);
    }

    private void handleComplexRegexException(final ComplexRegexException e, final Expression expression) {
        messageBuffer.clear();
        messageBuffer.append("Regex Expression '")
                .append(expression.getDebugId())
                .append("' is too complex to process with the input data. ")
                .append("This may be due to repetitive alternative paths, e.g. '(A|B)*', ")
                .append("in which case consider changing the pattern to use possessive quantifiers.");
        final Matcher matcher = e.getMatcher();
        if (matcher != null) {
            final int len = matcher.regionEnd() - matcher.regionStart();
            messageBuffer.append(" Pattern '")
                    .append(matcher.pattern())
                    .append("'.")
                    .append(" Input data region [")
                    .append(matcher.regionStart())
                    .append(",")
                    .append(matcher.regionEnd())
                    .append("] (length " + len + ").");
        }
        final String message = messageBuffer.toString();
        log(Severity.ERROR, message, e);
    }
}
