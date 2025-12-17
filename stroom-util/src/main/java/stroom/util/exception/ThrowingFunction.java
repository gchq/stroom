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

package stroom.util.exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Throwable> {

    R apply(T t) throws E;

    /**
     * Wraps a function that throws a checked exception with a catch block that will wrap
     * any thrown exception with a {@link RuntimeException}, thus making it unchecked and
     * usable in a lambda.
     */
    static <T, R, E extends Throwable> Function<T, R> unchecked(final ThrowingFunction<T, R, E> f) {
        return t -> {
            try {
                return f.apply(t);
            } catch (final Throwable e) {
                if (e instanceof final IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } else {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
