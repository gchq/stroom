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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractXsltFunctionTest<T extends StroomExtensionFunctionCall> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractXsltFunctionTest.class);

    @Mock
    private LocationFactory mockLocationFactory;
    @Mock
    private ErrorReceiver mockErrorReceiver;
    @Mock
    private XPathContext mockXPathContext;

    // Captors for the args to errorReceiver.log/logTemplate
    @Captor
    private ArgumentCaptor<Severity> severityCaptor;
    @Captor
    private ArgumentCaptor<String> messageCaptor;
    @Captor
    private ArgumentCaptor<Location> locationCaptor;
    @Captor
    private ArgumentCaptor<Throwable> throwableCaptor;
    @Captor
    private ArgumentCaptor<ElementId> elementIdCaptor;

    /**
     * Call the function with simple java objects as arguments. These will be converted
     * to a Sequence[]
     *
     * @param args
     * @return
     * @throws XPathException
     */
    protected Sequence callFunctionWithSimpleArgs(final Object... args) {

        // Convert our simple java objects (e.g. String, long, etc.) into Sequence[]
        final Sequence[] functionArgs = buildFunctionArguments(args);
        return callFunctionWithSequenceArgs(functionArgs);
    }

    protected Sequence callFunctionWithSequenceArgs(final Sequence[] args) {
        final T xsltFunction = getXsltFunction();
        final List<PipelineReference> pipelineReferences = getPipelineReferences();
        xsltFunction.configure(mockErrorReceiver, mockLocationFactory, pipelineReferences);

        final String functionName = getFunctionName();
        LOGGER.debug("Calling {} with args: {}", functionName, args);
        final Sequence sequence;
        try {
            sequence = xsltFunction.call(functionName, mockXPathContext, args);
        } catch (final XPathException e) {
            throw new RuntimeException(
                    "Error calling function " + functionName + ": " + e.getMessage(), e);
        }

        LOGGER.debug("Result type: {}, value: '{}'",
                NullSafe.toString(
                        sequence,
                        sequence2 -> sequence2.getClass().getSimpleName()),
                getAsStringValue(sequence).orElse("EMPTY"));
        return sequence;
    }

    /**
     * Log any calls to the {@link ErrorReceiver} to DEBUG.
     * Only call this method if you expect at least one call, else you will need to
     * set {@link Mockito} strictness to lenient.
     */
    protected void logLogCallsToDebug() {
        Mockito.doAnswer(invocation -> {
            final Severity severity = invocation.getArgument(0);
            final String message = invocation.getArgument(3);
            final Throwable throwable = invocation.getArgument(4);

            final String box = LogUtil.inBox("{}: {}{}",
                    severity,
                    message,
                    NullSafe.getOrElse(
                            throwable,
                            t -> " - ("
                                 + t.getClass().getSimpleName()
                                 + ": "
                                 + t.getMessage()
                                 + ")",
                            ""));

            LOGGER.debug("Call to mock ErrorReceiver.log():\n{}", box);

            return null;
        }).when(getMockErrorReceiver()).log(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any());
    }

    /**
     * Assert {@link ErrorReceiver#log(Severity, Location, ElementId, String, Throwable)} is never called
     */
    protected void verifyNoLogCalls() {
        verifyLogCalls(0);
    }

    protected LogArgs verifySingleLogCall() {
        return verifyLogCalls(1).get(0);
    }

    /**
     * Assert the number of times {@link ErrorReceiver#log(Severity, Location, ElementId, String, Throwable)}
     * is called and get all the call args.
     *
     * @param callCount Expected number of calls
     * @return All args used in the calls
     */
    protected List<LogArgs> verifyLogCalls(final int callCount) {
        Mockito.verify(getMockErrorReceiver(), Mockito.times(callCount)).log(
                severityCaptor.capture(),
                locationCaptor.capture(),
                elementIdCaptor.capture(),
                messageCaptor.capture(),
                throwableCaptor.capture());

        if (callCount == 0) {
            return Collections.emptyList();
        } else {
            final List<LogArgs> logArgsList = new ArrayList<>();
            for (int i = 0; i < callCount; i++) {
                final LogArgs logArgs = new LogArgs(
                        severityCaptor.getAllValues().get(i),
                        locationCaptor.getAllValues().get(i),
                        elementIdCaptor.getAllValues().get(i),
                        messageCaptor.getAllValues().get(i),
                        throwableCaptor.getAllValues().get(i));
                LOGGER.debug("log called with args: {}", logArgs);
                logArgsList.add(logArgs);
            }
            return logArgsList;
        }
    }

    protected void assertLogCall(final LogArgs logArgs,
                                 final Severity expectedSeverity,
                                 final String... expectedMessageParts) {
        if (logArgs != null) {
            Assertions.assertThat(logArgs.getSeverity())
                    .isEqualTo(expectedSeverity);

            for (final String expectedMessagePart : expectedMessageParts) {
                Assertions.assertThat(logArgs.getMessage())
                        .containsIgnoringCase(expectedMessagePart);
            }
        }
    }

    protected static Optional<String> getAsStringValue(final Sequence sequence) {
        return Optional.ofNullable(sequence)
                .map(sequence2 -> {
                    if (sequence2 instanceof EmptyAtomicSequence) {
                        return null;
                    } else if (sequence2 instanceof StringValue) {
                        final String str = ((StringValue) sequence2).getStringValue();
                        LOGGER.debug("Got string value:\n{}", str);
                        return str;
                    } else {
                        return sequence.toString();
                    }
                });
    }

    protected static Optional<String> getAsDateTimeValue(final Sequence sequence) {
        return Optional.ofNullable(sequence)
                .map(sequence2 -> {
                    if (sequence2 instanceof EmptyAtomicSequence) {
                        return null;
                    } else if (sequence2 instanceof DateTimeValue) {
                        final String str = ((DateTimeValue) sequence2).getStringValue();
                        LOGGER.debug("Got dateTime value:\n{}", str);
                        return str;
                    } else {
                        return sequence.toString();
                    }
                });
    }

    protected static Optional<Long> getAsLongValue(final Sequence sequence) {
        return Optional.ofNullable(sequence)
                .map(sequence2 -> {
                    if (sequence2 instanceof EmptyAtomicSequence) {
                        return null;
                    } else if (sequence2 instanceof Int64Value) {
                        final long val = ((Int64Value) sequence2).longValue();
                        LOGGER.debug("Got long value:\n{}", val);
                        return val;
                    } else if (sequence2 instanceof StringValue) {
                        final String str = ((StringValue) sequence2).getStringValue();
                        final long val = Long.parseLong(str);
                        LOGGER.debug("Got long value:\n{}", val);
                        return val;
                    } else {
                        return Long.parseLong(sequence.toString());
                    }
                });
    }

    protected static Optional<Double> getAsDoubleValue(final Sequence sequence) {
        return Optional.ofNullable(sequence)
                .map(sequence2 -> {
                    if (sequence2 instanceof EmptyAtomicSequence) {
                        return null;
                    } else if (sequence2 instanceof DoubleValue) {
                        final double val = ((DoubleValue) sequence2).getDoubleValue();
                        LOGGER.debug("Got double value:\n{}", val);
                        return val;
                    } else {
                        return Double.parseDouble(sequence.toString());
                    }
                });
    }

    protected static Optional<Boolean> getAsBooleanValue(final Sequence sequence) {
        return Optional.ofNullable(sequence)
                .map(sequence2 -> {
                    if (sequence2 instanceof EmptyAtomicSequence) {
                        return null;
                    } else if (sequence2 instanceof BooleanValue) {
                        final boolean val = ((BooleanValue) sequence2).getBooleanValue();
                        LOGGER.debug("Got boolean value:\n{}", val);
                        return val;
                    } else {
                        return Boolean.parseBoolean(sequence.toString());
                    }
                });
    }

    protected static Optional<String> getAsSerialisedXmlString(final Sequence sequence) {
        return Optional.ofNullable(sequence)
                .map(sequence2 -> {
                    if (sequence2 instanceof EmptyAtomicSequence) {
                        return null;
                    } else if (sequence2 instanceof NodeInfo) {
                        try {
                            final String xml = QueryResult.serialize((NodeInfo) sequence2);
                            LOGGER.debug("Got XML value:\n{}", xml);
                            return xml;
                        } catch (final XPathException e) {
                            throw new RuntimeException("Error serialising nodeInfo - "
                                                       + e.getMessage(), e);
                        }
                    } else {
                        return sequence.toString();
                    }
                });
    }

    /**
     * @return A constructed instance of the function T
     */
    abstract T getXsltFunction();

    /**
     * @return The name of the function as used in XLST content
     */
    // TODO: 26/01/2023 Ideally StroomExtensionFunctionCall would have a getName method
    abstract String getFunctionName();

    /**
     * Override this to provide pipeline references to the function, else none are supplied
     * to it.
     */
    protected List<PipelineReference> getPipelineReferences() {
        return Collections.emptyList();
    }

    /**
     * @return The {@link ErrorReceiver} that is configured on the function
     */
    public ErrorReceiver getMockErrorReceiver() {
        return mockErrorReceiver;
    }

    /**
     * @return The {@link LocationFactory} that is configured on the function
     */
    public LocationFactory getMockLocationFactory() {
        return mockLocationFactory;
    }

    /**
     * @return The {@link XPathContext} that is configured on the function
     */
    public XPathContext getMockXPathContext() {
        return mockXPathContext;
    }

    static Sequence[] buildFunctionArguments(final Object... args) {
        final List<Object> argsList = Arrays.asList(args);
        return buildFunctionArguments(argsList);
    }

    /**
     * Converts a list of objects into an array of {@link Sequence}
     *
     * @param args
     * @return
     */
    static Sequence[] buildFunctionArguments(final List<Object> args) {
        if (NullSafe.hasItems(args)) {
            final Sequence[] seqArr = new Sequence[args.size()];
            for (int i = 0; i < args.size(); i++) {
                final Object val = args.get(i);
                final Item item;

                if (val == null) {
                    item = null;
                } else if (val instanceof Boolean) {
                    item = BooleanValue.get((Boolean) val);
                } else if (val instanceof Instant) {
                    item = convertInstantArg((Instant) val);
                } else if (val instanceof DateTimeValue) {
                    item = (DateTimeValue) val;
                } else {
                    item = StringValue.makeStringValue(val.toString());
                }
                seqArr[i] = item;
            }
            return seqArr;
        } else {
            return new Sequence[0];
        }
    }

    private static Item convertInstantArg(final Instant val) {
        final Item item;
        item = StringValue.makeStringValue(DateUtil.createNormalDateTimeString(
                val.toEpochMilli()));
        return item;
    }

    protected static class LogArgs {

        private final Severity severity;
        private final String message;
        private final Location location;
        private final ElementId elementId;
        private final Throwable throwable;

        public LogArgs(final Severity severity,
                       final Location location,
                       final ElementId elementId,
                       final String message,
                       final Throwable throwable) {
            this.severity = severity;
            this.message = message;
            this.location = location;
            this.elementId = elementId;
            this.throwable = throwable;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public Location getLocation() {
            return location;
        }

        public ElementId getElementId() {
            return elementId;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        @Override
        public String toString() {
            return "LogArgs{" +
                   "severity=" + severity +
                   ", msg='" + message + '\'' +
                   ", location=" + location +
                   ", elementId='" + elementId + '\'' +
                   ", throwable=" + throwable +
                   '}';
        }
    }
}
