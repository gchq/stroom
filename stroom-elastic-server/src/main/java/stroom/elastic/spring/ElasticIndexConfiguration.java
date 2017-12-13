package stroom.elastic.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.elastic.server.ElasticIndexService;
import stroom.elastic.shared.ElasticIndex;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.importexport.server.ImportExportActionHandlers;

import javax.inject.Inject;

@Configuration
@ComponentScan(basePackages = {"stroom.elastic.server"}, excludeFilters = {
        // Exclude other configurations that might be found accidentally during
        // a component scan as configurations should be specified explicitly.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class ElasticIndexConfiguration {
    @Inject
    public ElasticIndexConfiguration(final ExplorerActionHandlers explorerActionHandlers,
                                     final ImportExportActionHandlers importExportActionHandlers,
                                     final ElasticIndexService indexService) {
        explorerActionHandlers.add(10, ElasticIndex.ENTITY_TYPE, ElasticIndex.ENTITY_TYPE, indexService);
        importExportActionHandlers.add(ElasticIndex.ENTITY_TYPE, indexService);
    }
}
