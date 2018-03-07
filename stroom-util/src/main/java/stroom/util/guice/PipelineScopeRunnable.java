package stroom.util.guice;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.atomic.AtomicInteger;

public class PipelineScopeRunnable {
    private final PipelineScope scope;
    private static final ThreadLocal<AtomicInteger> threadLocal = new ThreadLocal<>();

    @Inject
    PipelineScopeRunnable(@Named("pipelineScope") final PipelineScope scope) {
        this.scope = scope;
    }

    public void scopeRunnable(final Runnable runnable) {
        AtomicInteger depth = threadLocal.get();
        if (depth == null) {
            depth = new AtomicInteger();
            threadLocal.set(depth);
        }

        if (depth.incrementAndGet() == 1) {
            scope.enter();
        }

        try {
//            // explicitly seed some seed objects...
//            scope.seed(Key.get(SomeObject.class), someObject);

            // create and access scoped objects
            runnable.run();

        } finally {
            if (depth.getAndDecrement() == 1) {
                scope.exit();
                threadLocal.set(null);
            }
        }
    }
}
