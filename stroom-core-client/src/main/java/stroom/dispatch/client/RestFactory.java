package stroom.dispatch.client;

import stroom.util.shared.ResultPage;

import com.google.inject.TypeLiteral;

import java.util.List;
import java.util.Set;

public interface RestFactory {

    /**
     * Returns a builder for creating a {@link Rest} instance for calling a RESTful
     * service.
     * Equivalent to passing {@code false} to {@link RestFactory#builder(boolean)},
     * i.e. does not fire {@link stroom.task.client.TaskStartEvent}
     * or {@link stroom.task.client.TaskEndEvent} events.
     * @return An untyped {@link RestBuilder} to set the return type of the REST service.
     */
    RestBuilder builder();

    /**
     * Returns a builder for creating a {@link Rest} instance for calling a RESTful
     * service.
     *
     * @param isQuiet Set to true to not fire {@link stroom.task.client.TaskStartEvent}
     *                or {@link stroom.task.client.TaskEndEvent} events.
     * @return An untyped {@link RestBuilder} to set the return type of the REST service.
     */
    RestBuilder builder(final boolean isQuiet);

    String getImportFileURL();

    /**
     * Deprecated, use {@link RestFactory#builder()} instead
     */
    @Deprecated
    <R> Rest<R> create();

    /**
     * Deprecated, use {@link RestFactory#builder(boolean)} instead
     */
    @Deprecated
    <R> Rest<R> createQuiet();


    // --------------------------------------------------------------------------------


    interface RestBuilder {

        /**
         * Create a {@link Rest} for a void return type
         */
        Rest<Void> forVoid();

        /**
         * Create a {@link Rest} for a boolean return type
         */
        Rest<Boolean> forBoolean();

        /**
         * Create a {@link Rest} for a simple return type with no generics,
         * e.g. {@link String}.
         */
        <R> Rest<R> forType(final Class<R> type);

        /**
         * Create a {@link Rest} for a given return type with generics,
         * e.g. For a return type of {@link java.util.Collection<String>}
         * <pre>{@code
         * RestFactory.build()
         *   .forWrappedType(new TypeLiteral<Collection<String>>(){ })}</pre>
         */
        <R> Rest<R> forWrappedType(final TypeLiteral<R> typeLiteral);

        /**
         * Create a {@link Rest} for a {@link List} return type with a given
         * non-generic item type, e.g. {@link String}.
         */
        <T> Rest<List<T>> forListOf(final Class<T> itemType);

        /**
         * Create a {@link Rest} for a {@link Set} return type with a given
         * non-generic item type, e.g. {@link String}.
         */
        <T> Rest<Set<T>> forSetOf(final Class<T> itemType);

        /**
         * Create a {@link Rest} for a {@link ResultPage} return type with a given
         * non-generic item type, e.g. {@link String}.
         */
        <T> Rest<ResultPage<T>> forResultPageOf(final Class<T> itemType);
    }
}
