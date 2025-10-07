package stroom.pathways.impl;

import stroom.pathways.shared.AddPathway;
import stroom.pathways.shared.DeletePathway;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.UpdatePathway;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class PathwaysService {

    private final PathwaysProcessor pathwaysProcessor;

    @Inject
    public PathwaysService(final PathwaysProcessor pathwaysProcessor) {
        this.pathwaysProcessor = pathwaysProcessor;
    }

    public ResultPage<Pathway> findPathways(final FindPathwayCriteria criteria) {
        return pathwaysProcessor.findPathways(criteria);
    }

    public Boolean addPathway(final AddPathway addPathway) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Boolean updatePathway(final UpdatePathway updatePathway) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Boolean deletePathway(final DeletePathway deletePathway) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
