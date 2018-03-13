package stroom.util.guice;

import javax.inject.Inject;
import javax.inject.Named;

public class PipelineScopeRunnable {
    private final PipelineScope scope;

    @Inject
    PipelineScopeRunnable(@Named("pipelineScope") final PipelineScope scope) {
        this.scope = scope;
    }

    public void scopeRunnable(final Runnable runnable) {
        scope.enter();
        try {
//            // explicitly seed some seed objects...
//            scope.seed(Key.get(SomeObject.class), someObject);

            // create and access scoped objects
            runnable.run();

        } finally {
            scope.exit();
        }
    }
}
