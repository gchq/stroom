/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.util.client;

import com.google.inject.Provider;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lazily initialises a value using the passed valueSupplier.
 * If onInitConsumer is provided, runs it once when valueSupplier is called.
 * NOT thread safe, therefore only for use in GWT.
 */
public class LazyValue<T> {

    private final Supplier<T> valueSupplier;
    private Consumer<T> onInitConsumer = null;

    private T value = null;

    /**
     * @param provider Called once when getValue is first called to initialise the value.
     * @throws NullPointerException if valueSupplier is null.
     */
    public LazyValue(final Provider<T> provider) {
        Objects.requireNonNull(provider);
        this.valueSupplier = provider::get;
        this.onInitConsumer = null;
    }

    /**
     * @param valueSupplier Called once when getValue is first called to initialise the value.
     * @throws NullPointerException if valueSupplier is null.
     */
    public LazyValue(final Supplier<T> valueSupplier) {
        Objects.requireNonNull(valueSupplier);
        this.valueSupplier = valueSupplier;
        this.onInitConsumer = null;
    }

    /**
     * @param provider       Called once when getValue is first called to initialise the value.
     * @param onInitConsumer Called once after valueSupplier is called and accepts the value
     *                       supplied by valueSupplier.
     * @throws NullPointerException if valueSupplier is null.
     */
    public LazyValue(final Provider<T> provider,
                     final Consumer<T> onInitConsumer) {
        Objects.requireNonNull(provider);
        this.valueSupplier = provider::get;
        this.onInitConsumer = onInitConsumer;
    }

    /**
     * @param valueSupplier  Called once when getValue is first called to initialise the value.
     * @param onInitConsumer Called once after valueSupplier is called and accepts the value
     *                       supplied by valueSupplier.
     * @throws NullPointerException if valueSupplier is null.
     */
    public LazyValue(final Supplier<T> valueSupplier,
                     final Consumer<T> onInitConsumer) {
        Objects.requireNonNull(valueSupplier);
        this.valueSupplier = valueSupplier;
        this.onInitConsumer = onInitConsumer;
    }

    /**
     * @return The value, initialising it if it has not already been initialised.
     * @throws NullPointerException if the value supplied by valueSupplier is null.
     */
    public T getValue() {
        if (this.value == null) {
            final T value = valueSupplier.get();
            Objects.requireNonNull(value);
            this.value = value;
            if (onInitConsumer != null) {
                onInitConsumer.accept(value);
            }
        }
        return value;
    }

    /**
     * @return True if the value has been initialised.
     */
    public boolean isInitialised() {
        return value != null;
    }

    /**
     * If the value has been initialised run the valueConsumer with the initialised value.
     * If it hasn't been initialised, it will remain in that state and valueConsumer will
     * not be called.
     */
    public void consumeIfInitialised(final Consumer<T> valueConsumer) {
        if (value != null) {
            valueConsumer.accept(value);
        }
    }
}
