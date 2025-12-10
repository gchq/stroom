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

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Credit for this class goes to
 * <a href="https://stackoverflow.com/a/27644392">MC Emperor on Stack Overflow</a>
 */
public class LambdaExceptionUtil {

    @FunctionalInterface
    public interface ConsumerWithExceptions<T, E extends Exception> {

        void accept(T t) throws E;
    }

    @FunctionalInterface
    public interface BiConsumerWithExceptions<T, U, E extends Exception> {

        void accept(T t, U u) throws E;
    }

    @FunctionalInterface
    public interface FunctionWithExceptions<T, R, E extends Exception> {

        R apply(T t) throws E;
    }

    @FunctionalInterface
    public interface SupplierWithExceptions<T, E extends Exception> {

        T get() throws E;
    }

    @FunctionalInterface
    public interface RunnableWithExceptions<E extends Exception> {

        void run() throws E;
    }

    /**
     * e.g:
     * .forEach(rethrowConsumer(name -> System.out.println(Class.forName(name))));
     * or
     * .forEach(rethrowConsumer(ClassNameUtil::println));
     */
    public static <E extends Exception> Runnable rethrowRunnable(final RunnableWithExceptions<E> runnable)
            throws E {
        return () -> {
            try {
                runnable.run();
            } catch (final Exception exception) {
                throwAsUnchecked(exception);
            }
        };
    }

    /**
     * e.g:
     * .forEach(rethrowConsumer(name -> System.out.println(Class.forName(name))));
     * or
     * .forEach(rethrowConsumer(ClassNameUtil::println));
     */
    public static <T, E extends Exception> Consumer<T> rethrowConsumer(final ConsumerWithExceptions<T, E> consumer)
            throws E {
        return t -> {
            try {
                consumer.accept(t);
            } catch (final Exception exception) {
                throwAsUnchecked(exception);
            }
        };
    }

    public static <T, U, E extends Exception> BiConsumer<T, U> rethrowBiConsumer(
            final BiConsumerWithExceptions<T, U, E> biConsumer)
            throws E {
        return (t, u) -> {
            try {
                biConsumer.accept(t, u);
            } catch (final Exception exception) {
                throwAsUnchecked(exception);
            }
        };
    }

    /**
     * e.g:
     * .map(rethrowFunction(name -> Class.forName(name)))
     * or
     * .map(rethrowFunction(Class::forName))
     */
    public static <T, R, E extends Exception> Function<T, R> rethrowFunction(
            final FunctionWithExceptions<T, R, E> function)
            throws E {
        return t -> {
            try {
                return function.apply(t);
            } catch (final Exception exception) {
                throwAsUnchecked(exception);
                return null;
            }
        };
    }

    /**
     * rethrowSupplier(() -> new StringJoiner(new String(new byte[]{77, 97, 114, 107}, "UTF-8"))),
     */
    public static <T, E extends Exception> Supplier<T> rethrowSupplier(
            final SupplierWithExceptions<T, E> function)
            throws E {
        return () -> {
            try {
                return function.get();
            } catch (final Exception exception) {
                throwAsUnchecked(exception);
                return null;
            }
        };
    }

    /**
     * uncheck(() -> Class.forName("xxx"));
     */
    public static void uncheck(final RunnableWithExceptions t) {
        try {
            t.run();
        } catch (final Exception exception) {
            throwAsUnchecked(exception);
        }
    }

    /**
     * uncheck(() -> Class.forName("xxx"));
     */
    public static <R, E extends Exception> R uncheck(final SupplierWithExceptions<R, E> supplier) {
        try {
            return supplier.get();
        } catch (final Exception exception) {
            throwAsUnchecked(exception);
            return null;
        }
    }

    /**
     * uncheck(Class::forName, "xxx");
     */
    public static <T, R, E extends Exception> R uncheck(final FunctionWithExceptions<T, R, E> function, final T t) {
        try {
            return function.apply(t);
        } catch (final Exception exception) {
            throwAsUnchecked(exception);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAsUnchecked(final Exception exception) throws E {
        throw (E) exception;
    }

}
