package stroom.statistics.impl.sql.filter;

import stroom.pipeline.factory.PipelineElementModule;

public class StatisticsElementsModule extends PipelineElementModule {

    @Override
    protected void configureElements() {
        bindElement(StatisticsFilter.class);
    }
}
