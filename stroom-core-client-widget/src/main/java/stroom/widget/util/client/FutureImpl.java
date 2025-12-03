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

import java.util.function.Consumer;

public class FutureImpl<T> implements Future<T> {

    private T result;
    private Throwable throwable;
    private Consumer<T> resultConsumer;
    private Consumer<Throwable> throwableConsumer;

    public FutureImpl<T> onSuccess(final Consumer<T> resultConsumer) {
        this.resultConsumer = resultConsumer;
        if (result != null && resultConsumer != null) {
            resultConsumer.accept(result);
        }
        return this;
    }

    public FutureImpl<T> onFailure(final Consumer<Throwable> throwableConsumer) {
        this.throwableConsumer = throwableConsumer;
        if (throwable != null && throwableConsumer != null) {
            throwableConsumer.accept(throwable);
        }
        return this;
    }

    public FutureImpl<T> setResult(final T result) {
        this.result = result;
        if (resultConsumer != null) {
            resultConsumer.accept(result);
        }
        return this;
    }

    public FutureImpl<T> setThrowable(final Throwable throwable) {
        this.throwable = throwable;
        if (throwableConsumer != null) {
            throwableConsumer.accept(throwable);
        }
        return this;
    }
}
