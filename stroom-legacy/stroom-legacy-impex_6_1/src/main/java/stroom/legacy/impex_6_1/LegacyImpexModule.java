package stroom.legacy.impex_6_1;

import stroom.dashboard.shared.DashboardDoc;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.api.ImportConverter;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.script.shared.ScriptDoc;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.inject.AbstractModule;

@Deprecated
public class LegacyImpexModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ImportConverter.class).to(ImportConverterImpl.class);
        DataMapConverterBinder.create(binder())
                .bind(DashboardDoc.TYPE, DashboardDataMapConverter.class)
                .bind(DictionaryDoc.TYPE, DictionaryDataMapConverter.class)
                .bind(ElasticClusterDoc.TYPE, ElasticClusterDataMapConverter.class)
                .bind(ElasticIndexDoc.TYPE, ElasticIndexDataMapConverter.class)
                .bind(FeedDoc.TYPE, FeedDataMapConverter.class)
                .bind(LuceneIndexDoc.TYPE, IndexDataMapConverter.class)
                .bind(PipelineDoc.TYPE, PipelineDataMapConverter.class)
                .bind(ScriptDoc.TYPE, ScriptDataMapConverter.class)
                .bind(SolrIndexDoc.TYPE, SolrIndexDataMapConverter.class)
                .bind(StatisticStoreDoc.TYPE, StatisticDataMapConverter.class)
                .bind(StroomStatsStoreDoc.TYPE, StroomStatsDataMapConverter.class)
                .bind(TextConverterDoc.TYPE, TextConverterDataMapConverter.class)
                .bind(XmlSchemaDoc.TYPE, XmlSchemaDataMapConverter.class)
                .bind(XsltDoc.TYPE, XsltDataMapConverter.class)
                .bind(VisualisationDoc.TYPE, VisualisationDataMapConverter.class);
    }
}
