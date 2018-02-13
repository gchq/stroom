package stroom.util.concurrent;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ModelCache<T> {
    private final Supplier<T> valueSupplier;
    private final Supplier<Long> timeSupplier;
    private final long maxAge;

    private volatile Model<T> model;
    private volatile boolean rebuildRequired = true;

    private ModelCache(final Supplier<T> valueSupplier,
                       final Supplier<Long> timeSupplier,
                       final long maxAge) {
        Objects.requireNonNull(valueSupplier);
        Objects.requireNonNull(timeSupplier);

        this.valueSupplier = valueSupplier;
        this.timeSupplier = timeSupplier;
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
        if (model.isOld()) {
            rebuildRequired = true;
        }

        while (rebuildRequired) {
            rebuildRequired = false;
            final T t = valueSupplier.get();
            this.model = new Model<>(t, maxAge);
        }
    }

    public void rebuild() {
        model.makeOld();
        rebuildRequired = true;
    }

    public static class Builder<T> {
        private static final long DEFAULT_MAX_AGE = TimeUnit.MINUTES.toMillis(10);

        private Supplier<T> valueSupplier;
        private Supplier<Long> timeSupplier = System::currentTimeMillis;
        private long maxAge = DEFAULT_MAX_AGE;

        public Builder<T> valueSupplier(final Supplier<T> supplier) {
            this.valueSupplier = supplier;
            return this;
        }

        public Builder<T> timeSupplier(final Supplier<Long> supplier) {
            this.timeSupplier = supplier;
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
            return new ModelCache<>(valueSupplier, timeSupplier, maxAge);
        }
    }

    private class Model<T> {
        private final long createTime;
        private final long maxTime;
        private final T t;
        private volatile boolean old;

        private Model(final T t, final long maxAge) {
            createTime = ModelCache.this.timeSupplier.get();
            maxTime = createTime + maxAge;
            this.t = t;
        }

        boolean isOld() {
            if (!old && maxTime < ModelCache.this.timeSupplier.get()) {
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
