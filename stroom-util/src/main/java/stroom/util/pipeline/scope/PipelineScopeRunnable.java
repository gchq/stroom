package stroom.util.pipeline.scope;

import jakarta.inject.Inject;

import java.util.function.Supplier;

public class PipelineScopeRunnable {

    private final PipelineScope scope;

    @Inject
    PipelineScopeRunnable(final PipelineScope scope) {
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

    public <T> T scopeResult(final Supplier<T> supplier) {
        final T result;

        scope.enter();
        try {
            // create and access scoped objects
            result = supplier.get();

        } finally {
            scope.exit();
        }

        return result;
    }
}
