package stroom.annotations.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.annotations.server.AnnotationsIndexService;
import stroom.annotations.shared.AnnotationsIndex;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.importexport.server.ImportExportActionHandlers;

import javax.inject.Inject;

@Configuration
@ComponentScan(basePackages = {"stroom.annotations.server"}, excludeFilters = {
        // Exclude other configurations that might be found accidentally during
        // a component scan as configurations should be specified explicitly.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class AnnotationsIndexConfiguration {
    @Inject
    public AnnotationsIndexConfiguration(final ExplorerActionHandlers explorerActionHandlers,
                                         final ImportExportActionHandlers importExportActionHandlers,
                                         final AnnotationsIndexService indexService) {
        explorerActionHandlers.add(30, AnnotationsIndex.ENTITY_TYPE, AnnotationsIndex.ENTITY_TYPE, indexService);
        importExportActionHandlers.add(AnnotationsIndex.ENTITY_TYPE, indexService);
    }
}
