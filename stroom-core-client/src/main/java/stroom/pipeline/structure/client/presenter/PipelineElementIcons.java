package stroom.pipeline.structure.client.presenter;

import stroom.pipeline.shared.data.PipelineElementIcon;
import stroom.svg.client.SvgImage;

public class PipelineElementIcons {

    public static SvgImage get(final PipelineElementIcon icon) {
        switch (icon) {
            case FILE:
                return SvgImage.FILE;
            case FILES:
                return SvgImage.FILES;
            case HADOOP:
                return SvgImage.HADOOP_ELEPHANT_LOGO;
            case ELASTIC_INDEX:
                return SvgImage.ELASTIC_INDEX;
            case ID:
                return SvgImage.ID;
            case INDEX:
                return SvgImage.INDEX;
            case JSON:
                return SvgImage.JSON;
            case KAFKA:
                return SvgImage.KAFKA;
            case RECORD_COUNT:
                return SvgImage.RECORD_COUNT;
            case RECORD_OUTPUT:
                return SvgImage.RECORD_OUTPUT;
            case REFERENCE_DATA:
                return SvgImage.REFERENCE_DATA;
            case SEARCH:
                return SvgImage.SEARCH;
            case XML_SEARCH:
                return SvgImage.XML_SEARCH;
            case SOLR:
                return SvgImage.SOLR;
            case SPLIT:
                return SvgImage.SPLIT;
            case STATISTICS:
                return SvgImage.STATISTICS;
            case STREAM:
                return SvgImage.STREAM;
            case STROOM_STATS:
                return SvgImage.STROOM_STATS;
            case TEXT:
                return SvgImage.TEXT;
            case XML:
                return SvgImage.XML;
            case XSD:
                return SvgImage.XSD;
            case XSLT:
                return SvgImage.XSLT;
        }
        return null;
    }

}
