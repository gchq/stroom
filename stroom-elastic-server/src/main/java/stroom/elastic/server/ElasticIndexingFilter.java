package stroom.elastic.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.query.api.v2.DocRef;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

/**
 * The index filter... takes the index XML and builds the LUCENE documents
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(
        type = "ElasticIndexingFilter",
        category = PipelineElementType.Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE
        },
        icon = ElementIcons.ELASTIC_SEARCH)
public class ElasticIndexingFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticIndexingFilter.class);

    private DocRef indexRef;

    private final ElasticIndexCache elasticIndexCache;

    @Inject
    public ElasticIndexingFilter(final ElasticIndexCache elasticIndexCache) {
        this.elasticIndexCache = elasticIndexCache;
    }

    @PipelineProperty(description = "The elastic index to send records to.")
    public void setIndex(final DocRef indexRef) {
        this.indexRef = indexRef;
    }
}
