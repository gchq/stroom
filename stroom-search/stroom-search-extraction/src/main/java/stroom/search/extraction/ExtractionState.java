package stroom.search.extraction;

import stroom.util.pipeline.scope.PipelineScoped;

@PipelineScoped
public class ExtractionState {

    private int count;

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        count++;
    }
}
