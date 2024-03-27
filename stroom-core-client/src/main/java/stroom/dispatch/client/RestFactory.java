package stroom.dispatch.client;

import stroom.util.shared.ResultPage;

import com.google.inject.TypeLiteral;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RestFactory {

    /**
     * Returns a builder for creating a {@link Rest} instance for calling a RESTful
     * service.
     *
     * @return An untyped {@link RestBuilder} to set the return type of the REST service.
     */
    RestBuilder builder();

    String getImportFileURL();


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
         * Create a {@link Rest} for a String return type
         */
        Rest<String> forString();

        /**
         * Create a {@link Rest} for a Integer return type
         */
        Rest<Integer> forInteger();

        /**
         * Create a {@link Rest} for a Long return type
         */
        Rest<Long> forLong();

        /**
         * Create a {@link Rest} for a simple return type with no generics,
         * e.g. {@link String} or {@link stroom.docref.DocRef}.
         */
        <R> Rest<R> forType(final Class<R> type);

        /**
         * Create a {@link Rest} for a given return type with generics,
         * e.g. For a return type of {@link java.util.Collection<String>}
         * <pre>{@code
         * RestFactory.build()
         *   .forWrappedType(new TypeLiteral<Collection<String>>(){ })}</pre>
         * <p>
         * Before you use this look at the other {@code forXXX} methods to see
         * if there is a pre-canned one for your wrapped type,
         * e.g. {@link RestBuilder#forResultPageOf(Class)}
         * </p>
         */
        <R> Rest<R> forWrappedType(final TypeLiteral<R> typeLiteral);

        /**
         * Create a {@link Rest} for a {@link List} return type with a given
         * non-generic item type, e.g. {@link String}.
         */
        <T> Rest<List<T>> forListOf(final Class<T> itemType);

        /**
         * Create a {@link Rest} for a {@link List} return type with a given
         * non-generic item type, e.g. {@link String}.
         */
        Rest<List<String>> forStringList();

        /**
         * Create a {@link Rest} for a {@link Set} return type with a given
         * non-generic item type, e.g. {@link String}.
         */
        <T> Rest<Set<T>> forSetOf(final Class<T> itemType);

        /**
         * Create a {@link Rest} for a {@link Map} return type with given
         * non-generic key and value types, e.g. a {@link Map} of {@link Long} to
         * {@link String}.
         * <p>
         * If either key or value type has generics then use {@link RestBuilder#forWrappedType(TypeLiteral)}.
         * </p>
         */
        <K, V> Rest<Map<K, V>> forMapOf(final Class<K> keyType, final Class<V> valueType);

        /**
         * Create a {@link Rest} for a {@link ResultPage} return type with a given
         * non-generic item type, e.g. {@link String}.
         */
        <T> Rest<ResultPage<T>> forResultPageOf(final Class<T> itemType);
    }
}