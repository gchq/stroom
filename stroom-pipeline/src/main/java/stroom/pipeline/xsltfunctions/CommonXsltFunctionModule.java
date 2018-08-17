package stroom.pipeline.xsltfunctions;

import net.sf.saxon.value.SequenceType;

import javax.inject.Inject;
import javax.inject.Provider;

public class CommonXsltFunctionModule extends AbstractXsltFunctionModule {
    @Override
    protected void configureFunctions() {
        bindFunction(ClassificationFunction.class);
        bindFunction(CurrentTimeFunction.class);
        bindFunction(CurrentUserFunction.class);
        bindFunction(DictionaryFunction.class);
        bindFunction(FeedAttributeFunction.class);
        bindFunction(FeedNameFunction.class);
        bindFunction(FetchJsonFunction.class);
        bindFunction(FormatDateFunction.class);
        bindFunction(GenerateURLFunction.class);
        bindFunction(GetFunction.class);
        bindFunction(HashFunction.class);
        bindFunction(HexToDecFunction.class);
        bindFunction(HexToOctFunction.class);
        bindFunction(JsonToXmlFunction.class);
        bindFunction(LogFunction.class);
        bindFunction(MetaFunction.class);
        bindFunction(NumericIPFunction.class);
        bindFunction(ParseUriFunction.class);
        bindFunction(PipelineNameFunction.class);
        bindFunction(PutFunction.class);
        bindFunction(RandomFunction.class);
        bindFunction(SearchIdFunction.class);
    }

    private static class ClassificationFunction extends StroomExtensionFunctionDefinition<Classification> {
        @Inject
        ClassificationFunction(final Provider<Classification> functionCallProvider) {
            super("classification", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class CurrentTimeFunction extends StroomExtensionFunctionDefinition<CurrentTime> {
        @Inject
        CurrentTimeFunction(final Provider<CurrentTime> functionCallProvider) {
            super("current-time", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class CurrentUserFunction extends StroomExtensionFunctionDefinition<CurrentUser> {
        @Inject
        CurrentUserFunction(final Provider<CurrentUser> functionCallProvider) {
            super("current-user", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class DictionaryFunction extends StroomExtensionFunctionDefinition<Dictionary> {
        @Inject
        DictionaryFunction(final Provider<Dictionary> functionCallProvider) {
            super("dictionary", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    // TODO : Deprecate
    @Deprecated
    private static class FeedAttributeFunction extends StroomExtensionFunctionDefinition<Meta> {
        @Inject
        FeedAttributeFunction(final Provider<Meta> functionCallProvider) {
            super("feed-attribute", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class FeedNameFunction extends StroomExtensionFunctionDefinition<FeedName> {
        @Inject
        FeedNameFunction(final Provider<FeedName> functionCallProvider) {
            super("feed-name", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class FetchJsonFunction extends StroomExtensionFunctionDefinition<FetchJson> {
        @Inject
        FetchJsonFunction(final Provider<FetchJson> functionCallProvider) {
            super("fetch-json", 2, 2, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING
            }, SequenceType.NODE_SEQUENCE, functionCallProvider);
        }
    }

    private static class FormatDateFunction extends StroomExtensionFunctionDefinition<FormatDate> {
        @Inject
        FormatDateFunction(final Provider<FormatDate> functionCallProvider) {
            super("format-date", 1, 5, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.OPTIONAL_STRING,
                    SequenceType.OPTIONAL_STRING,
                    SequenceType.OPTIONAL_STRING,
                    SequenceType.OPTIONAL_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class GenerateURLFunction extends StroomExtensionFunctionDefinition<GenerateURL> {
        @Inject
        GenerateURLFunction(final Provider<GenerateURL> functionCallProvider) {
            super("generate-url", 4, 4, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class GetFunction extends StroomExtensionFunctionDefinition<Get> {
        @Inject
        GetFunction(final Provider<Get> functionCallProvider) {
            super("get", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class HashFunction extends StroomExtensionFunctionDefinition<Hash> {
        @Inject
        HashFunction(final Provider<Hash> functionCallProvider) {
            super("hash", 1, 3, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.OPTIONAL_STRING,
                    SequenceType.OPTIONAL_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class HexToDecFunction extends StroomExtensionFunctionDefinition<HexToDec> {
        @Inject
        HexToDecFunction(final Provider<HexToDec> functionCallProvider) {
            super("hex-to-dec", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class HexToOctFunction extends StroomExtensionFunctionDefinition<HexToDec> {
        @Inject
        HexToOctFunction(final Provider<HexToDec> functionCallProvider) {
            super("hex-to-oct", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class JsonToXmlFunction extends StroomExtensionFunctionDefinition<JsonToXml> {
        @Inject
        JsonToXmlFunction(final Provider<JsonToXml> functionCallProvider) {
            super("json-to-xml", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.NODE_SEQUENCE, functionCallProvider);
        }
    }

    private static class LogFunction extends StroomExtensionFunctionDefinition<Log> {
        @Inject
        LogFunction(final Provider<Log> functionCallProvider) {
            super("log", 2, 2, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING
            }, SequenceType.EMPTY_SEQUENCE, functionCallProvider);
        }
    }

    private static class MetaFunction extends StroomExtensionFunctionDefinition<Meta> {
        @Inject
        MetaFunction(final Provider<Meta> functionCallProvider) {
            super("meta", 2, 5, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class NumericIPFunction extends StroomExtensionFunctionDefinition<NumericIP> {
        @Inject
        NumericIPFunction(final Provider<NumericIP> functionCallProvider) {
            super("numeric-ip", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class ParseUriFunction extends StroomExtensionFunctionDefinition<ParseUri> {
        @Inject
        ParseUriFunction(final Provider<ParseUri> functionCallProvider) {
            super("parse-uri", 1, 1, new SequenceType[]{
                    SequenceType.SINGLE_STRING
            }, SequenceType.NODE_SEQUENCE, functionCallProvider);
        }
    }

    private static class PipelineNameFunction extends StroomExtensionFunctionDefinition<PipelineName> {
        @Inject
        PipelineNameFunction(final Provider<PipelineName> functionCallProvider) {
            super("pipeline-name", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }

    private static class PutFunction extends StroomExtensionFunctionDefinition<Put> {
        @Inject
        PutFunction(final Provider<Put> functionCallProvider) {
            super("put", 2, 2, new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING
            }, SequenceType.EMPTY_SEQUENCE, functionCallProvider);
        }
    }

    private static class RandomFunction extends StroomExtensionFunctionDefinition<Random> {
        @Inject
        RandomFunction(final Provider<Random> functionCallProvider) {
            super("random", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_DOUBLE, functionCallProvider);
        }
    }

    private static class SearchIdFunction extends StroomExtensionFunctionDefinition<SearchId> {
        @Inject
        SearchIdFunction(final Provider<SearchId> functionCallProvider) {
            super("search-id", 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING, functionCallProvider);
        }
    }
}
