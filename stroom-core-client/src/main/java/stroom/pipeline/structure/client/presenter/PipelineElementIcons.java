package stroom.pipeline.structure.client.presenter;

import stroom.pipeline.shared.data.PipelineElementIcon;
import stroom.svg.client.SvgImage;

public class PipelineElementIcons {

    public static SvgImage get(final PipelineElementIcon icon) {
        switch (icon) {
            case FILE:
                return SvgImage.PIPELINE_FILE;
            case FILES:
                return SvgImage.PIPELINE_FILES;
            case HADOOP:
                return SvgImage.PIPELINE_HADOOP;
            case ELASTIC_INDEX:
                return SvgImage.PIPELINE_ELASTIC_INDEX;
            case ID:
                return SvgImage.PIPELINE_ID;
            case INDEX:
                return SvgImage.PIPELINE_INDEX;
            case JSON:
                return SvgImage.PIPELINE_JSON;
            case KAFKA:
                return SvgImage.PIPELINE_KAFKA;
            case RECORD_COUNT:
                return SvgImage.PIPELINE_RECORD_COUNT;
            case RECORD_OUTPUT:
                return SvgImage.PIPELINE_RECORD_OUTPUT;
            case REFERENCE_DATA:
                return SvgImage.PIPELINE_REFERENCE_DATA;
            case SEARCH_OUTPUT:
                return SvgImage.PIPELINE_SEARCH_OUTPUT;
            case XML_SEARCH:
                return SvgImage.PIPELINE_XML_SEARCH;
            case SOLR:
                return SvgImage.PIPELINE_SOLR;
            case SPLIT:
                return SvgImage.PIPELINE_SPLIT;
            case STATISTICS:
                return SvgImage.PIPELINE_STATISTICS;
            case STREAM:
                return SvgImage.PIPELINE_STREAM;
            case STROOM_STATS:
                return SvgImage.PIPELINE_STROOM_STATS;
            case TEXT:
                return SvgImage.PIPELINE_TEXT;
            case XML:
                return SvgImage.PIPELINE_XML;
            case XSD:
                return SvgImage.PIPELINE_XSD;
            case XSLT:
                return SvgImage.PIPELINE_XSLT;
        }
        return null;
    }
}
