package stroom.pipeline.xsltfunctions;

import net.sf.saxon.value.SequenceType;

import javax.inject.Inject;
import javax.inject.Provider;

public class DataStoreXsltFunctionModule extends AbstractXsltFunctionModule {
    @Override
    protected void configureFunctions() {
        bindFunction(BitmapLookupFunction.class);
        bindFunction(LookupFunction.class);
        bindFunction(PartNoFunction.class);
        bindFunction(SourceIdFunction.class);
        bindFunction(StreamIdFunction.class);
    }

    private static class BitmapLookupFunction extends StroomExtensionFunctionDefinition<BitmapLookup> {
        @Inject
        BitmapLookupFunction(final Provider<BitmapLookup> functionCallProvider) {
            super("bitmap-lookup", 2, 5, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING,
                    SequenceType.OPTIONAL_STRING,
                    SequenceType.OPTIONAL_BOOLEAN,
                    SequenceType.OPTIONAL_BOOLEAN
            }, SequenceType.NODE_SEQUENCE, functionCallProvider);
        }
    }

    private static class LookupFunction extends StroomExtensionFunctionDefinition<Lookup> {
        @Inject
        LookupFunction(final Provider<Lookup> functionCallProvider) {
            super("lookup", 2, 5, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING,
                    SequenceType.OPTIONAL_STRING,
                    SequenceType.OPTIONAL_BOOLEAN,
                    SequenceType.OPTIONAL_BOOLEAN
            }, SequenceType.NODE_SEQUENCE, functionCallProvider);
        }
    }

    private static class PartNoFunction extends StroomExtensionFunctionDefinition<PartNo> {
        @Inject
        PartNoFunction(final Provider<PartNo> functionCallProvider) {
            super("part-no", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class SourceIdFunction extends StroomExtensionFunctionDefinition<SourceId> {
        @Inject
        SourceIdFunction(final Provider<SourceId> functionCallProvider) {
            super("source-id", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class StreamIdFunction extends StroomExtensionFunctionDefinition<SourceId> {
        @Inject
        StreamIdFunction(final Provider<SourceId> functionCallProvider) {
            super("stream-id", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }
}
