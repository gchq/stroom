package stroom.pipeline.xsltfunctions;

import stroom.util.pipeline.scope.PipelineScoped;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.SequenceType;

public class CommonXsltFunctionModule extends AbstractXsltFunctionModule {

    @Override
    protected void configure() {
        bind(TaskScopeMap.class).in(PipelineScoped.class);
        super.configure();
    }

    @Override
    protected void configureFunctions() {
        bindFunction(AddMetaFunction.class);
        bindFunction(CidrToNumericIPRangeFunction.class);
        bindFunction(ClassificationFunction.class);
        bindFunction(ColFromFunction.class);
        bindFunction(ColToFunction.class);
        bindFunction(CurrentTimeFunction.class);
        bindFunction(CurrentUnixTimeFunction.class);
        bindFunction(CurrentUserFunction.class);
        bindFunction(DecodeUrlFunction.class);
        bindFunction(DictionaryFunction.class);
        bindFunction(EncodeUrlFunction.class);
        bindFunction(FeedAttributeFunction.class);
        bindFunction(FeedNameFunction.class);
        bindFunction(FetchJsonFunction.class);
        bindFunction(FormatDateFunction.class);
        bindFunction(FormatDateTimeFunction.class);
        bindFunction(FromUnixTimeFunction.class);
        bindFunction(GetFunction.class);
        bindFunction(HashFunction.class);
        bindFunction(HexToDecFunction.class);
        bindFunction(HexToOctFunction.class);
        bindFunction(HexToStringFunction.class);
        bindFunction(HostAddressFunction.class);
        bindFunction(HostNameFunction.class);
        bindFunction(HttpCallFunction.class);
        bindFunction(JsonToXmlFunction.class);
        bindFunction(LineFromFunction.class);
        bindFunction(LineToFunction.class);
        bindFunction(LinkFunction.class);
        bindFunction(LogFunction.class);
        bindFunction(ManifestFunction.class);
        bindFunction(ManifestForIdFunction.class);
        bindFunction(MetaFunction.class);
        bindFunction(MetaAttributesFunction.class);
        bindFunction(MetaKeysFunction.class);
        bindFunction(MetaStreamFunction.class);
        bindFunction(MetaStreamForIdFunction.class);
        bindFunction(NumericIPFunction.class);
        bindFunction(IPInCidrFunction.class);
        bindFunction(ParseDateTimeFunction.class);
        bindFunction(ParseUriFunction.class);
        bindFunction(PipelineNameFunction.class);
        bindFunction(PointIsInsideXYPolygonFunction.class);
        bindFunction(PutFunction.class);
        bindFunction(RandomFunction.class);
        bindFunction(RecordNoFunction.class);
        bindFunction(SearchIdFunction.class);
        bindFunction(SplitDocumentFunction.class);
        bindFunction(SourceFunction.class);
        bindFunction(ToUnixTimeFunction.class);
    }

    private static class AddMetaFunction extends StroomExtensionFunctionDefinition<AddMeta> {

        @Inject
        AddMetaFunction(final Provider<AddMeta> functionCallProvider) {
            super(
                    AddMeta.FUNCTION_NAME,
                    2,
                    2,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_STRING},
                    SequenceType.EMPTY_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class CidrToNumericIPRangeFunction extends StroomExtensionFunctionDefinition<CidrToNumericIPRange> {

        @Inject
        CidrToNumericIPRangeFunction(final Provider<CidrToNumericIPRange> functionCallProvider) {
            super(
                    CidrToNumericIPRange.FUNCTION_NAME,
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    ArrayItemType.SINGLE_ARRAY,
                    functionCallProvider);
        }
    }

    private static class ClassificationFunction extends StroomExtensionFunctionDefinition<Classification> {

        @Inject
        ClassificationFunction(final Provider<Classification> functionCallProvider) {
            super(
                    "classification",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class ColFromFunction extends StroomExtensionFunctionDefinition<ColFrom> {

        @Inject
        ColFromFunction(final Provider<ColFrom> functionCallProvider) {
            super(
                    "col-from",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class ColToFunction extends StroomExtensionFunctionDefinition<ColTo> {

        @Inject
        ColToFunction(final Provider<ColTo> functionCallProvider) {
            super(
                    "col-to",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class CurrentTimeFunction extends StroomExtensionFunctionDefinition<CurrentTime> {

        @Inject
        CurrentTimeFunction(final Provider<CurrentTime> functionCallProvider) {
            super(
                    CurrentTime.FUNCTION_NAME,
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class CurrentUnixTimeFunction extends StroomExtensionFunctionDefinition<CurrentUnixTime> {

        @Inject
        CurrentUnixTimeFunction(final Provider<CurrentUnixTime> functionCallProvider) {
            super(
                    CurrentUnixTime.FUNCTION_NAME,
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_INTEGER,
                    functionCallProvider);
        }
    }

    private static class CurrentUserFunction extends StroomExtensionFunctionDefinition<CurrentUser> {

        @Inject
        CurrentUserFunction(final Provider<CurrentUser> functionCallProvider) {
            super(
                    CurrentUser.FUNCTION_NAME,
                    0,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class DecodeUrlFunction extends StroomExtensionFunctionDefinition<DecodeUrl> {

        @Inject
        DecodeUrlFunction(final Provider<DecodeUrl> functionCallProvider) {
            super(
                    "decode-url",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class DictionaryFunction extends StroomExtensionFunctionDefinition<Dictionary> {

        @Inject
        DictionaryFunction(final Provider<Dictionary> functionCallProvider) {
            super(
                    "dictionary",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class EncodeUrlFunction extends StroomExtensionFunctionDefinition<EncodeUrl> {

        @Inject
        EncodeUrlFunction(final Provider<EncodeUrl> functionCallProvider) {
            super(
                    "encode-url",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    // TODO : Deprecate
    @Deprecated
    private static class FeedAttributeFunction extends StroomExtensionFunctionDefinition<Meta> {

        @Inject
        FeedAttributeFunction(final Provider<Meta> functionCallProvider) {
            super(
                    "feed-attribute",
                    1,
                    1, new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class FeedNameFunction extends StroomExtensionFunctionDefinition<FeedName> {

        @Inject
        FeedNameFunction(final Provider<FeedName> functionCallProvider) {
            super(
                    "feed-name",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class FetchJsonFunction extends StroomExtensionFunctionDefinition<FetchJson> {

        @Inject
        FetchJsonFunction(final Provider<FetchJson> functionCallProvider) {
            super(
                    "fetch-json",
                    1,
                    2,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.OPTIONAL_STRING
                    },
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class FormatDateFunction extends StroomExtensionFunctionDefinition<FormatDate> {

        @Inject
        FormatDateFunction(final Provider<FormatDate> functionCallProvider) {
            super(
                    FormatDate.FUNCTION_NAME,
                    1,
                    5,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING
                    },
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class ParseDateTimeFunction extends StroomExtensionFunctionDefinition<ParseDateTime> {

        @Inject
        ParseDateTimeFunction(final Provider<ParseDateTime> functionCallProvider) {
            super(
                    ParseDateTime.FUNCTION_NAME,
                    1,
                    3,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING
                    },
                    SequenceType.OPTIONAL_DATE_TIME,
                    functionCallProvider);
        }
    }

    private static class FormatDateTimeFunction extends StroomExtensionFunctionDefinition<FormatDateTime> {

        @Inject
        FormatDateTimeFunction(final Provider<FormatDateTime> functionCallProvider) {
            super(
                    FormatDateTime.FUNCTION_NAME,
                    1,
                    3,
                    new SequenceType[]{
                            SequenceType.OPTIONAL_DATE_TIME,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING
                    },
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class FromUnixTimeFunction extends StroomExtensionFunctionDefinition<FromUnixTime> {

        @Inject
        FromUnixTimeFunction(final Provider<FromUnixTime> functionCallProvider) {
            super(
                    FromUnixTime.FUNCTION_NAME,
                    1,
                    1,
                    new SequenceType[]{
                            SequenceType.SINGLE_INTEGER
                    },
                    SequenceType.OPTIONAL_DATE_TIME,
                    functionCallProvider);
        }
    }

    private static class GetFunction extends StroomExtensionFunctionDefinition<Get> {

        @Inject
        GetFunction(final Provider<Get> functionCallProvider) {
            super(
                    Get.FUNCTION_NAME,
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class HashFunction extends StroomExtensionFunctionDefinition<Hash> {

        @Inject
        HashFunction(final Provider<Hash> functionCallProvider) {
            super(
                    Hash.FUNCTION_NAME,
                    1,
                    3,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class HexToDecFunction extends StroomExtensionFunctionDefinition<HexToDec> {

        @Inject
        HexToDecFunction(final Provider<HexToDec> functionCallProvider) {
            super(
                    HexToDec.FUNCTION_NAME,
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class HexToOctFunction extends StroomExtensionFunctionDefinition<HexToOct> {

        @Inject
        HexToOctFunction(final Provider<HexToOct> functionCallProvider) {
            super(
                    "hex-to-oct",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class HexToStringFunction extends StroomExtensionFunctionDefinition<HexToString> {

        @Inject
        HexToStringFunction(final Provider<HexToString> functionCallProvider) {
            super(
                    HexToString.FUNCTION_NAME,
                    2,
                    2,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_STRING
                    },
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class HostAddressFunction extends StroomExtensionFunctionDefinition<HostAddress> {

        @Inject
        HostAddressFunction(final Provider<HostAddress> functionCallProvider) {
            super(
                    "host-address",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class HostNameFunction extends StroomExtensionFunctionDefinition<HostName> {

        @Inject
        HostNameFunction(final Provider<HostName> functionCallProvider) {
            super(
                    "host-name",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class HttpCallFunction extends StroomExtensionFunctionDefinition<HttpCall> {

        @Inject
        HttpCallFunction(final Provider<HttpCall> functionCallProvider) {
            super(
                    HttpCall.FUNCTION_NAME,
                    1,
                    5,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class JsonToXmlFunction extends StroomExtensionFunctionDefinition<JsonToXml> {

        @Inject
        JsonToXmlFunction(final Provider<JsonToXml> functionCallProvider) {
            super(
                    JsonToXml.FUNCTION_NAME,
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class LineFromFunction extends StroomExtensionFunctionDefinition<LineFrom> {

        @Inject
        LineFromFunction(final Provider<LineFrom> functionCallProvider) {
            super(
                    "line-from",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class LineToFunction extends StroomExtensionFunctionDefinition<LineTo> {

        @Inject
        LineToFunction(final Provider<LineTo> functionCallProvider) {
            super(
                    "line-to",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class LinkFunction extends StroomExtensionFunctionDefinition<Link> {

        @Inject
        LinkFunction(final Provider<Link> functionCallProvider) {
            super(
                    "link",
                    1,
                    3,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.OPTIONAL_STRING,
                            SequenceType.OPTIONAL_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class LogFunction extends StroomExtensionFunctionDefinition<Log> {

        @Inject
        LogFunction(final Provider<Log> functionCallProvider) {
            super(
                    Log.FUNCTION_NAME,
                    2,
                    2,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_STRING},
                    SequenceType.EMPTY_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class ManifestFunction extends StroomExtensionFunctionDefinition<Manifest> {

        @Inject
        ManifestFunction(final Provider<Manifest> functionCallProvider) {
            super(
                    Manifest.FUNCTION_NAME_NO_ARGS,
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class ManifestForIdFunction extends StroomExtensionFunctionDefinition<Manifest> {

        @Inject
        ManifestForIdFunction(final Provider<Manifest> functionCallProvider) {
            super(
                    Manifest.FUNCTION_NAME_FOR_ID,
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class MetaFunction extends StroomExtensionFunctionDefinition<Meta> {

        @Inject
        MetaFunction(final Provider<Meta> functionCallProvider) {
            super(
                    "meta",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class MetaAttributesFunction extends StroomExtensionFunctionDefinition<MetaAttribute> {

        @Inject
        MetaAttributesFunction(final Provider<MetaAttribute> functionCallProvider) {
            super(
                    "meta-attribute",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class MetaKeysFunction extends StroomExtensionFunctionDefinition<MetaKeys> {

        @Inject
        MetaKeysFunction(final Provider<MetaKeys> functionCallProvider) {
            super(
                    "meta-keys",
                    0,
                    0,
                    new SequenceType[]{},
                    ArrayItemType.SINGLE_ARRAY,
                    functionCallProvider);
        }
    }

    private static class MetaStreamFunction extends StroomExtensionFunctionDefinition<MetaStream> {

        @Inject
        MetaStreamFunction(final Provider<MetaStream> functionCallProvider) {
            super(
                    MetaStream.FUNCTION_NAME_NO_ARGS,
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class MetaStreamForIdFunction extends StroomExtensionFunctionDefinition<MetaStream> {

        @Inject
        MetaStreamForIdFunction(final Provider<MetaStream> functionCallProvider) {
            super(
                    MetaStream.FUNCTION_NAME_FOR_ID,
                    2,
                    2,
                    new SequenceType[]{SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_INTEGER},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class NumericIPFunction extends StroomExtensionFunctionDefinition<NumericIP> {

        @Inject
        NumericIPFunction(final Provider<NumericIP> functionCallProvider) {
            super(
                    "numeric-ip",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class IPInCidrFunction extends StroomExtensionFunctionDefinition<IPInCidr> {

        @Inject
        IPInCidrFunction(final Provider<IPInCidr> functionCallProvider) {
            super(
                    IPInCidr.FUNCTION_NAME,
                    2,
                    2,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_STRING
                    },
                    SequenceType.SINGLE_BOOLEAN,
                    functionCallProvider);
        }
    }

    private static class ParseUriFunction extends StroomExtensionFunctionDefinition<ParseUri> {

        @Inject
        ParseUriFunction(final Provider<ParseUri> functionCallProvider) {
            super(
                    "parse-uri",
                    1,
                    1,
                    new SequenceType[]{SequenceType.SINGLE_STRING},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class PipelineNameFunction extends StroomExtensionFunctionDefinition<PipelineName> {

        @Inject
        PipelineNameFunction(final Provider<PipelineName> functionCallProvider) {
            super(
                    "pipeline-name",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class PointIsInsideXYPolygonFunction
            extends StroomExtensionFunctionDefinition<PointIsInsideXYPolygon> {

        @Inject
        PointIsInsideXYPolygonFunction(final Provider<PointIsInsideXYPolygon> functionCallProvider) {
            // TODO This really ought to be in lower-kebab-case like all the others but that would
            //  break content packs that use this func.
            super(
                    "pointIsInsideXYPolygon",
                    4,
                    4,
                    new SequenceType[]{
                            SequenceType.SINGLE_DOUBLE,
                            SequenceType.SINGLE_DOUBLE,
                            BuiltInAtomicType.DOUBLE.zeroOrMore(),
                            BuiltInAtomicType.DOUBLE.zeroOrMore()},
                    SequenceType.SINGLE_BOOLEAN,
                    functionCallProvider);
        }
    }

    private static class PutFunction extends StroomExtensionFunctionDefinition<Put> {

        @Inject
        PutFunction(final Provider<Put> functionCallProvider) {
            super(
                    Put.FUNCTION_NAME,
                    2,
                    2,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_STRING},
                    SequenceType.EMPTY_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class RandomFunction extends StroomExtensionFunctionDefinition<Random> {

        @Inject
        RandomFunction(final Provider<Random> functionCallProvider) {
            super(
                    "random",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_DOUBLE,
                    functionCallProvider);
        }
    }

    private static class RecordNoFunction extends StroomExtensionFunctionDefinition<RecordNo> {

        @Inject
        RecordNoFunction(final Provider<RecordNo> functionCallProvider) {
            super(
                    "record-no",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class SearchIdFunction extends StroomExtensionFunctionDefinition<SearchId> {

        @Inject
        SearchIdFunction(final Provider<SearchId> functionCallProvider) {
            super(
                    "search-id",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }

    private static class SplitDocumentFunction extends StroomExtensionFunctionDefinition<SplitDocument> {

        @Inject
        SplitDocumentFunction(final Provider<SplitDocument> functionCallProvider) {
            super(
                    SplitDocument.FUNCTION_NAME,
                    3,
                    3,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_NUMERIC,
                            SequenceType.SINGLE_NUMERIC
                    },
                    ArrayItemType.SINGLE_ARRAY,
                    functionCallProvider);
        }
    }

    private static class SourceFunction extends StroomExtensionFunctionDefinition<Source> {

        @Inject
        SourceFunction(final Provider<Source> functionCallProvider) {
            super(
                    "source",
                    0,
                    0,
                    new SequenceType[]{},
                    SequenceType.NODE_SEQUENCE,
                    functionCallProvider);
        }
    }

    private static class ToUnixTimeFunction extends StroomExtensionFunctionDefinition<ToUnixTime> {

        @Inject
        ToUnixTimeFunction(final Provider<ToUnixTime> functionCallProvider) {
            super(
                    ToUnixTime.FUNCTION_NAME,
                    1,
                    1,
                    new SequenceType[]{
                            SequenceType.OPTIONAL_DATE_TIME
                    },
                    SequenceType.OPTIONAL_INTEGER,
                    functionCallProvider);
        }
    }
}
