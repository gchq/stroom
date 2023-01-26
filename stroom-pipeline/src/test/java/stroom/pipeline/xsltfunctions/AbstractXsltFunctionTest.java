package stroom.pipeline.xsltfunctions;

import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractXsltFunctionTest<T extends StroomExtensionFunctionCall> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractXsltFunctionTest.class);

    @Mock
    private LocationFactory mockLocationFactory;
    @Mock
    private ErrorReceiver mockErrorReceiver;
    @Mock
    private XPathContext mockXPathContext;

    /**
     * Call the function with simple java objects as arguments. These will be converted
     * to a Sequence[]
     * @param args
     * @return
     * @throws XPathException
     */
    protected Sequence callFunctionWithSimpleArgs(final Object... args) throws XPathException {

        // Convert our simple java objects (e.g. String, long, etc.) into Sequence[]
        final Sequence[] functionArgs = buildFunctionArguments(args);
        return callFunctionWithSequenceArgs(functionArgs);
    }

    protected Sequence callFunctionWithSequenceArgs(final Sequence[] args) throws XPathException {
        final T xsltFunction = getXsltFunction();
        final List<PipelineReference> pipelineReferences = getPipelineReferences();
        xsltFunction.configure(mockErrorReceiver, mockLocationFactory, pipelineReferences);

        final String functionName = getFunctionName();
        LOGGER.debug("Calling {} with args: {}", functionName, args);
        final Sequence sequence = xsltFunction.call(functionName, mockXPathContext, args);
        return sequence;
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
        return buildFunctionArguments(List.of(args));
    }

    /**
     * Converts a list of objects into an array of {@link Sequence}
     * @param args
     * @return
     */
    static Sequence[] buildFunctionArguments(final List<Object> args) {
        if (NullSafe.hasItems(args)) {
            Sequence[] seqArr = new Sequence[args.size()];
            for (int i = 0; i < args.size(); i++) {
                final Object val = args.get(i);
                final Item item;

                if (val instanceof Boolean) {
                    item = BooleanValue.get((Boolean) val);
                } else if (val instanceof Instant) {
                    item = convertInstantArg((Instant) val);
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
}
