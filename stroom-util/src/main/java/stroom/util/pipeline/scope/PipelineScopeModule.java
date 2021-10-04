package stroom.util.pipeline.scope;

import com.google.inject.AbstractModule;

public class PipelineScopeModule extends AbstractModule {

    public void configure() {
        final PipelineScope pipelineScope = new PipelineScope();

        // tell Guice about the scope
        bindScope(PipelineScoped.class, pipelineScope);

        // make our scope instance injectable
        bind(PipelineScope.class).toInstance(pipelineScope);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
