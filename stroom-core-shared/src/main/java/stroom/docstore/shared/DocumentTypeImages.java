package stroom.docstore.shared;

import stroom.svg.shared.SvgImage;

public class DocumentTypeImages {

    public static SvgImage get(final String type) {
        switch (type) {
            case "AnalyticRule":
                return SvgImage.DOCUMENT_ANALYTIC_RULE;
            case "AnalyticOutputStore":
                return SvgImage.DOCUMENT_ANALYTIC_OUTPUT_STORE;
            case "AnnotationsIndex":
                return SvgImage.DOCUMENT_ANNOTATIONS_INDEX;
            case "Dashboard":
                return SvgImage.DOCUMENT_DASHBOARD;
            case "Dictionary":
                return SvgImage.DOCUMENT_DICTIONARY;
            case "ElasticCluster":
                return SvgImage.DOCUMENT_ELASTIC_CLUSTER;
            case "ElasticIndex":
                return SvgImage.DOCUMENT_ELASTIC_INDEX;
            case "Favourites":
                return SvgImage.DOCUMENT_FAVOURITES;
            case "Feed":
                return SvgImage.DOCUMENT_FEED;
            case "Folder":
                return SvgImage.DOCUMENT_FOLDER;
            case "Index":
                return SvgImage.DOCUMENT_INDEX;
            case "KafkaConfig":
                return SvgImage.DOCUMENT_KAFKA_CONFIG;
            case "Pipeline":
                return SvgImage.DOCUMENT_PIPELINE;
            case "Query":
                return SvgImage.DOCUMENT_QUERY;
            case "ReceiveDataRuleSet":
                return SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET;
            case "Script":
                return SvgImage.DOCUMENT_SCRIPT;
//            case "searchable":
//                return SvgImage.DOCUMENT_searchable;
            case "SelectAllOrNone":
                return SvgImage.DOCUMENT_SELECT_ALL_OR_NONE;
            case "SolrIndex":
                return SvgImage.DOCUMENT_SOLR_INDEX;
            case "StatisticStore":
                return SvgImage.DOCUMENT_STATISTIC_STORE;
            case "StroomStatsStore":
                return SvgImage.DOCUMENT_STROOM_STATS_STORE;
            case "System":
                return SvgImage.DOCUMENT_SYSTEM;
            case "TextConverter":
                return SvgImage.DOCUMENT_TEXT_CONVERTER;
            case "View":
                return SvgImage.DOCUMENT_VIEW;
            case "Visualisation":
                return SvgImage.DOCUMENT_VISUALISATION;
            case "XMLSchema":
                return SvgImage.DOCUMENT_XMLSCHEMA;
            case "XSLT":
                return SvgImage.DOCUMENT_XSLT;
        }
        return null;
    }
}
