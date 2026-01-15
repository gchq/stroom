/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.docstore.shared;

import stroom.explorer.shared.ExplorerConstants;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DocumentTypeRegistry {

    private static final Map<String, DocumentType> MAP = new HashMap<>();

    public static final DocumentType SYSTEM_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SYSTEM,
            ExplorerConstants.SYSTEM,
            ExplorerConstants.SYSTEM,
            SvgImage.DOCUMENT_SYSTEM);
    public static final DocumentType FAVOURITES_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SYSTEM,
            "Favourites",
            "Favourites",
            SvgImage.DOCUMENT_FAVOURITES);
    public static final DocumentType FOLDER_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.STRUCTURE,
            ExplorerConstants.FOLDER_TYPE,
            ExplorerConstants.FOLDER_TYPE,
            SvgImage.FOLDER);
    public static final DocumentType PROCESSOR_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.DATA_PROCESSING,
            "Processor",
            "Processor",
            SvgImage.DOCUMENT_PIPELINE);
    public static final DocumentType PROCESSOR_FILTER_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.DATA_PROCESSING,
            "ProcessorFilter",
            "Processor Filter",
            SvgImage.FILTER);
    public static final DocumentType ANALYTIC_RULE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "AnalyticRule",
            "Analytic Rule",
            SvgImage.DOCUMENT_ANALYTIC_RULE);
    public static final DocumentType ANALYTICS_STORE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Analytics",
            "Analytics",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType DASHBOARD_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Dashboard",
            "Dashboard",
            SvgImage.DOCUMENT_DASHBOARD);
    public static final DocumentType DICTIONARY_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "Dictionary",
            "Dictionary",
            SvgImage.DOCUMENT_DICTIONARY);
    public static final DocumentType LUCENE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "Index",
            "Lucene Index",
            SvgImage.DOCUMENT_INDEX);
    public static final DocumentType REPORT_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Report",
            "Report",
            SvgImage.DOCUMENT_REPORT);
    public static final DocumentType STROOM_STATS_STORE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "StroomStatsStore",
            "Stroom-Stats Store",
            SvgImage.DOCUMENT_STROOM_STATS_STORE);
    public static final DocumentType XML_SCHEMA_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.TRANSFORMATION,
            "XMLSchema",
            "XML Schema",
            SvgImage.DOCUMENT_XMLSCHEMA);
    public static final DocumentType KAFKA_CONFIG_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "KafkaConfig",
            "Kafka Configuration",
            SvgImage.DOCUMENT_KAFKA_CONFIG);
    public static final DocumentType SOLR_INDEX_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "SolrIndex",
            "Solr Index",
            SvgImage.DOCUMENT_SOLR_INDEX);
    public static final DocumentType SCYLLA_DB_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "ScyllaDB",
            "Scylla DB",
            SvgImage.DOCUMENT_SCYLLA_DB);
    public static final DocumentType DOCUMENTATION_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "Documentation",
            "Documentation",
            SvgImage.DOCUMENT_DOCUMENTATION);
    public static final DocumentType ELASTIC_CLUSTER_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "ElasticCluster",
            "Elastic Cluster",
            SvgImage.DOCUMENT_ELASTIC_CLUSTER);
    public static final DocumentType ELASTIC_INDEX_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "ElasticIndex",
            "Elastic Index",
            SvgImage.DOCUMENT_ELASTIC_INDEX);
    public static final DocumentType STATE_STORE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "StateStore",
            "State Store",
            SvgImage.DOCUMENT_STATE_STORE);
    public static final DocumentType PLAN_B_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "PlanB",
            "Plan B",
            SvgImage.DOCUMENT_PLAN_B);
    public static final DocumentType STATISTIC_STORE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "StatisticStore",
            "Statistic Store",
            SvgImage.DOCUMENT_STATISTIC_STORE);
    public static final DocumentType FEED_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.DATA_PROCESSING,
            "Feed",
            "Feed",
            SvgImage.DOCUMENT_FEED);
    public static final DocumentType OPENAI_MODEL_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "OpenAIModel",
            "OpenAI Model",
            SvgImage.DOCUMENT_OPEN_AI);
    public static final DocumentType PIPELINE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.DATA_PROCESSING,
            "Pipeline",
            "Pipeline",
            SvgImage.DOCUMENT_PIPELINE);
    public static final DocumentType QUERY_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Query",
            "Query",
            SvgImage.DOCUMENT_QUERY);
    public static final DocumentType RECEIVE_DATA_RULESET_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "ReceiveDataRuleSet",
            "Rule Set",
            SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET);
    public static final DocumentType S3_CONFIG_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "S3Config",
            "S3 Configuration",
            SvgImage.DOCUMENT_S3);
    public static final DocumentType GIT_REPO_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "GitRepo",
            "Git Repo",
            SvgImage.DOCUMENT_GIT_REPO_FOLDER);
    public static final DocumentType SCRIPT_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "Script",
            "Script",
            SvgImage.DOCUMENT_SCRIPT);
    public static final DocumentType TEXT_CONVERTER_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.TRANSFORMATION,
            "TextConverter",
            "Text Converter",
            SvgImage.DOCUMENT_TEXT_CONVERTER);
    public static final DocumentType VIEW_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "View",
            "View",
            SvgImage.DOCUMENT_VIEW);
    public static final DocumentType VISUALISATION_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            "Visualisation",
            "Visualisation",
            SvgImage.DOCUMENT_VISUALISATION);
    public static final DocumentType XSLT_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.TRANSFORMATION,
            "XSLT",
            "XSL Translation",
            SvgImage.DOCUMENT_XSLT);
    public static final DocumentType PATHWAYS_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            "Pathways",
            "Pathways",
            SvgImage.DOCUMENT_PATHWAYS);


    public static final DocumentType DUAL_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Dual",
            "Dual",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType REF_STORE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "ReferenceDataStore",
            "Reference Data Store (This Node Only)",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType TASK_MANAGER_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "TaskManager",
            "Task Manager",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType ANNOTATIONS_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Annotations",
            "Annotations",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType ANNOTATION_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Annotation",
            "Annotation",
            SvgImage.EDIT);
    public static final DocumentType ANNOTATION_TAG_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "AnnotationGroup",
            "Annotation Group",
            SvgImage.EDIT);
    public static final DocumentType STREAM_STORE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "StreamStore",
            "Stream Store",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType INDEX_SHARDS_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "IndexShards",
            "Index Shards",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType PROCESSOR_TASK_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "ProcessorTasks",
            "Processor Tasks",
            SvgImage.DOCUMENT_SEARCHABLE);
    public static final DocumentType PLAN_B_SHARD_INFO_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "PlanBShards",
            "Plan B Shards",
            SvgImage.DOCUMENT_SEARCHABLE);


    static {
        put(ANALYTICS_STORE_DOCUMENT_TYPE);
        put(ANALYTIC_RULE_DOCUMENT_TYPE);
        put(ANNOTATION_DOCUMENT_TYPE);
        put(DASHBOARD_DOCUMENT_TYPE);
        put(DICTIONARY_DOCUMENT_TYPE);
        put(DOCUMENTATION_DOCUMENT_TYPE);
        put(ELASTIC_CLUSTER_DOCUMENT_TYPE);
        put(ELASTIC_INDEX_DOCUMENT_TYPE);
        put(FAVOURITES_DOCUMENT_TYPE);
        put(FEED_DOCUMENT_TYPE);
        put(FOLDER_DOCUMENT_TYPE);
        put(KAFKA_CONFIG_DOCUMENT_TYPE);
        put(LUCENE_DOCUMENT_TYPE);
        put(OPENAI_MODEL_DOCUMENT_TYPE);
        put(PIPELINE_DOCUMENT_TYPE);
        put(PROCESSOR_DOCUMENT_TYPE);
        put(PROCESSOR_FILTER_DOCUMENT_TYPE);
        put(QUERY_DOCUMENT_TYPE);
        put(RECEIVE_DATA_RULESET_DOCUMENT_TYPE);
        put(REPORT_DOCUMENT_TYPE);
        put(S3_CONFIG_DOCUMENT_TYPE);
        put(SCRIPT_DOCUMENT_TYPE);
        put(GIT_REPO_DOCUMENT_TYPE);
        put(SCYLLA_DB_DOCUMENT_TYPE);
        put(SOLR_INDEX_DOCUMENT_TYPE);
        put(STATE_STORE_DOCUMENT_TYPE);
        put(PLAN_B_DOCUMENT_TYPE);
        put(STATISTIC_STORE_DOCUMENT_TYPE);
        put(STROOM_STATS_STORE_DOCUMENT_TYPE);
        put(SYSTEM_DOCUMENT_TYPE);
        put(TEXT_CONVERTER_DOCUMENT_TYPE);
        put(VIEW_DOCUMENT_TYPE);
        put(VISUALISATION_DOCUMENT_TYPE);
        put(XML_SCHEMA_DOCUMENT_TYPE);
        put(XSLT_DOCUMENT_TYPE);
        put(PATHWAYS_DOCUMENT_TYPE);

        // Searchables
        put(DUAL_DOCUMENT_TYPE);
        put(REF_STORE_DOCUMENT_TYPE);
        put(TASK_MANAGER_DOCUMENT_TYPE);
        put(ANNOTATIONS_DOCUMENT_TYPE);
        put(STREAM_STORE_DOCUMENT_TYPE);
        put(INDEX_SHARDS_DOCUMENT_TYPE);
        put(PROCESSOR_TASK_DOCUMENT_TYPE);
        put(PLAN_B_SHARD_INFO_DOCUMENT_TYPE);
    }

    private static void put(final DocumentType documentType) {
        final DocumentType existing = MAP.put(documentType.getType(), documentType);
        if (existing != null) {
            throw new RuntimeException("A document type is already registered for '" + documentType.getType() + "'");
        }
    }

    public static DocumentType get(final String type) {
        return MAP.get(type);
    }

    public static SvgImage getIcon(final String type) {
        return NullSafe.get(MAP.get(type), DocumentType::getIcon);
    }

    public static Collection<DocumentType> getTypes() {
        return MAP.values();
    }
}
