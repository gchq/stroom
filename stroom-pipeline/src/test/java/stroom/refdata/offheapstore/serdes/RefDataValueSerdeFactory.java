package stroom.refdata.offheapstore.serdes;

import com.google.common.collect.ImmutableMap;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.StringValue;

import java.util.Map;

/**
 * Saves having to use Guice in the tests and the type map defined in {@link stroom.pipeline.PipelineModule}
 */
public class RefDataValueSerdeFactory {

    private RefDataValueSerdeFactory() {
    }

    private static final Map<Integer, RefDataValueSubSerde> TYPE_TO_SERDE_MAP = ImmutableMap.of(
            FastInfosetValue.TYPE_ID, new FastInfoSetValueSerde(),
            StringValue.TYPE_ID, new StringValueSerde());

    public static RefDataValueSerde create() {
        return new RefDataValueSerde(TYPE_TO_SERDE_MAP);
    }
}
