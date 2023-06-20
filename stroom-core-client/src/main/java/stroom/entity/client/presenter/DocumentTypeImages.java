package stroom.entity.client.presenter;

import stroom.explorer.shared.DocumentIcon;
import stroom.explorer.shared.DocumentType;
import stroom.svg.client.SvgImage;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Optional;

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

    public static SafeHtml getSafeHtml(final DocumentIcon icon) {
        return Optional.ofNullable(get(icon))
                .map(SvgImage::getSvg)
                .map(SafeHtmlUtils::fromSafeConstant)
                .orElse(SafeHtmlUtils.EMPTY_SAFE_HTML);
    }

    public static SafeHtml getSafeHtml(final DocumentType documentType) {
        return Optional.ofNullable(get(documentType.getIcon()))
                .map(SvgImage::getSvg)
                .map(SafeHtmlUtils::fromSafeConstant)
                .orElse(SafeHtmlUtils.EMPTY_SAFE_HTML);
    }

    public static SvgImage get(final DocumentIcon icon) {
        switch (icon) {
            case ANALYTIC_RULE:
                return SvgImage.DOCUMENT_ANALYTIC_RULE;
            case ANALYTIC_OUTPUT_STORE:
                return SvgImage.DOCUMENT_ANALYTIC_OUTPUT_STORE;
            case ANNOTATIONS_INDEX:
                return SvgImage.DOCUMENT_ANNOTATIONS_INDEX;
            case DASHBOARD:
                return SvgImage.DOCUMENT_DASHBOARD;
            case DICTIONARY:
                return SvgImage.DOCUMENT_DICTIONARY;
            case ELASTIC_CLUSTER:
                return SvgImage.DOCUMENT_ELASTIC_CLUSTER;
            case ELASTIC_INDEX:
                return SvgImage.DOCUMENT_ELASTIC_INDEX;
            case FAVOURITES:
                return SvgImage.DOCUMENT_FAVOURITES;
            case FEED:
                return SvgImage.DOCUMENT_FEED;
            case FOLDER:
                return SvgImage.DOCUMENT_FOLDER;
            case INDEX:
                return SvgImage.DOCUMENT_INDEX;
            case KAFKA_CONFIG:
                return SvgImage.DOCUMENT_KAFKA_CONFIG;
            case PIPELINE:
                return SvgImage.DOCUMENT_PIPELINE;
            case QUERY:
                return SvgImage.DOCUMENT_QUERY;
            case RECEIVE_DATA_RULE_SET:
                return SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET;
            case SCRIPT:
                return SvgImage.DOCUMENT_SCRIPT;
            case SEARCHABLE:
                return SvgImage.DOCUMENT_SEARCHABLE;
            case SELECT_ALL_OR_NONE:
                return SvgImage.DOCUMENT_SELECT_ALL_OR_NONE;
            case SOLR_INDEX:
                return SvgImage.DOCUMENT_SOLR_INDEX;
            case STATISTIC_STORE:
                return SvgImage.DOCUMENT_STATISTIC_STORE;
            case STROOM_STATS_STORE:
                return SvgImage.DOCUMENT_STROOM_STATS_STORE;
            case SYSTEM:
                return SvgImage.DOCUMENT_SYSTEM;
            case TEXT_CONVERTER:
                return SvgImage.DOCUMENT_TEXT_CONVERTER;
            case VIEW:
                return SvgImage.DOCUMENT_VIEW;
            case VISUALISATION:
                return SvgImage.DOCUMENT_VISUALISATION;
            case XML_SCHEMA:
                return SvgImage.DOCUMENT_XMLSCHEMA;
            case XSLT:
                return SvgImage.DOCUMENT_XSLT;
        }
        return null;
    }
}
