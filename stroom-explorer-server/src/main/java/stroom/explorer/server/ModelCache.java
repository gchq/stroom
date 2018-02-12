package stroom.explorer.server;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ModelCache<T> {
    private final Supplier<T> supplier;
    private final long maxAge;

    private volatile Model<T> model;
    private volatile boolean rebuildRequired = true;

    private ModelCache(final Supplier<T> supplier, final long maxAge) {
        Objects.requireNonNull(supplier);

        this.supplier = supplier;
        this.maxAge = maxAge;

        model = new Model<>(null, maxAge);

        // Ensure the model is marked as needing rebuilding.
        rebuild();
    }

    public T get() {
        Model<T> model = this.model;
        if (!model.isOld()) {
            return model.get();
        }

        create();

        return this.model.get();
    }

    private synchronized void create() {
        while (rebuildRequired) {
            rebuildRequired = false;
            final T t = supplier.get();
            Model<T> model = new Model<>(t, maxAge);
            this.model = model;
        }
    }

    public void rebuild() {
        model.makeOld();
        rebuildRequired = true;
    }

    public static class Builder<T> {
        private static final long DEFAULT_MAX_AGE = TimeUnit.MINUTES.toMillis(10);

        private Supplier<T> supplier;
        private long maxAge = DEFAULT_MAX_AGE;

        public Builder<T> supplier(final Supplier<T> supplier) {
            this.supplier = supplier;
            return this;
        }

        public Builder<T> maxAge(final long age, final TimeUnit timeUnit) {
            this.maxAge = timeUnit.toMillis(age);
            return this;
        }

        public Builder<T> maxAge(final long millis) {
            this.maxAge = millis;
            return this;
        }

        public ModelCache<T> build() {
            return new ModelCache<>(supplier, maxAge);
        }
    }

    private static class Model<T> {
        private final long createTime;
        private final long maxTime;
        private final T t;
        private volatile boolean old;

        private Model(final T t, final long maxAge) {
            createTime = System.currentTimeMillis();
            maxTime = createTime + maxAge;
            this.t = t;
        }

        boolean isOld() {
            if (!old && maxTime < System.currentTimeMillis()) {
                old = true;
            }
            return old;
        }

        void makeOld() {
            old = true;
        }

        T get() {
            return t;
        }
    }
}
