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
                return SvgImage.ANALYTIC_RULE;
            case "AnalyticOutputStore":
                return SvgImage.ANALYTIC_OUTPUT_STORE;
            case "AnnotationsIndex":
                return SvgImage.ANNOTATIONS_INDEX;
            case "Dashboard":
                return SvgImage.DASHBOARD;
            case "Dictionary":
                return SvgImage.DICTIONARY;
            case "ElasticCluster":
                return SvgImage.ELASTIC_CLUSTER;
            case "ElasticIndex":
                return SvgImage.ELASTIC_INDEX;
            case "Favourites":
                return SvgImage.FAVOURITES;
            case "Feed":
                return SvgImage.FEED;
            case "Folder":
                return SvgImage.FOLDER;
            case "Index":
                return SvgImage.INDEX;
            case "KafkaConfig":
                return SvgImage.KAFKA_CONFIG;
            case "Pipeline":
                return SvgImage.PIPELINE;
            case "Query":
                return SvgImage.QUERY;
            case "ReceiveDataRuleSet":
                return SvgImage.RECEIVE_DATA_RULE_SET;
            case "Script":
                return SvgImage.SCRIPT;
//            case "searchable":
//                return SvgImage.searchable;
            case "SelectAllOrNone":
                return SvgImage.SELECT_ALL_OR_NONE;
            case "SolrIndex":
                return SvgImage.SOLR_INDEX;
            case "StatisticStore":
                return SvgImage.STATISTIC_STORE;
            case "StroomStatsStore":
                return SvgImage.STROOM_STATS_STORE;
            case "System":
                return SvgImage.SYSTEM;
            case "TextConverter":
                return SvgImage.TEXT_CONVERTER;
            case "View":
                return SvgImage.VIEW;
            case "Visualisation":
                return SvgImage.VISUALISATION;
            case "XMLSchema":
                return SvgImage.XMLSCHEMA;
            case "XSLT":
                return SvgImage.XSLT;
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
                return SvgImage.ANALYTIC_RULE;
            case ANALYTIC_OUTPUT_STORE:
                return SvgImage.ANALYTIC_OUTPUT_STORE;
            case ANNOTATIONS_INDEX:
                return SvgImage.ANNOTATIONS_INDEX;
            case DASHBOARD:
                return SvgImage.DASHBOARD;
            case DICTIONARY:
                return SvgImage.DICTIONARY;
            case ELASTIC_CLUSTER:
                return SvgImage.ELASTIC_CLUSTER;
            case ELASTIC_INDEX:
                return SvgImage.ELASTIC_INDEX;
            case FAVOURITES:
                return SvgImage.FAVOURITES;
            case FEED:
                return SvgImage.FEED;
            case FOLDER:
                return SvgImage.FOLDER;
            case INDEX:
                return SvgImage.INDEX;
            case KAFKA_CONFIG:
                return SvgImage.KAFKA_CONFIG;
            case PIPELINE:
                return SvgImage.PIPELINE;
            case QUERY:
                return SvgImage.QUERY;
            case RECEIVE_DATA_RULE_SET:
                return SvgImage.RECEIVE_DATA_RULE_SET;
            case SCRIPT:
                return SvgImage.SCRIPT;
            case SEARCHABLE:
                return SvgImage.SEARCHABLE;
            case SELECT_ALL_OR_NONE:
                return SvgImage.SELECT_ALL_OR_NONE;
            case SOLR_INDEX:
                return SvgImage.SOLR_INDEX;
            case STATISTIC_STORE:
                return SvgImage.STATISTIC_STORE;
            case STROOM_STATS_STORE:
                return SvgImage.STROOM_STATS_STORE;
            case SYSTEM:
                return SvgImage.SYSTEM;
            case TEXT_CONVERTER:
                return SvgImage.TEXT_CONVERTER;
            case VIEW:
                return SvgImage.VIEW;
            case VISUALISATION:
                return SvgImage.VISUALISATION;
            case XML_SCHEMA:
                return SvgImage.XMLSCHEMA;
            case XSLT:
                return SvgImage.XSLT;
        }
        return null;
    }
}
