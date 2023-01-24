package stroom.test.common.util.xsltfunctions;

import stroom.util.NullSafe;
import stroom.util.date.DateUtil;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

import java.time.Instant;
import java.util.List;

public class XsltFunctionTestUtil {

    private XsltFunctionTestUtil() {
    }

    public static Sequence[] buildFunctionArguments(final Object... args) {
        return buildFunctionArguments(List.of(args));
    }
    /**
     * Converts a list of objects into an array of {@link Sequence}
     * @param args
     * @return
     */
    public static Sequence[] buildFunctionArguments(final List<Object> args) {
        if (NullSafe.hasItems(args)) {
            Sequence[] seqArr = new Sequence[args.size()];
            for (int i = 0; i < args.size(); i++) {
                final Object val = args.get(i);
                final Item item;
                if (val instanceof Boolean) {
                    item = BooleanValue.get((Boolean) val);
                } else if (val instanceof Instant) {

                    item = StringValue.makeStringValue(DateUtil.createNormalDateTimeString(
                            ((Instant) val).toEpochMilli()));
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
}
