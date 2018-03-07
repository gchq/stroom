package stroom.util.guice;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class PipelineScopeModule extends AbstractModule {
    public void configure() {
        final PipelineScope pipelineScope = new PipelineScope();

        // tell Guice about the scope
        bindScope(PipelineScoped.class, pipelineScope);

        // make our scope instance injectable
        bind(PipelineScope.class).annotatedWith(Names.named("pipelineScope")).toInstance(pipelineScope);
    }
}