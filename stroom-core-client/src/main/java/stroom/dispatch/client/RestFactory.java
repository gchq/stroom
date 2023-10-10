package stroom.dispatch.client;

import com.google.inject.TypeLiteral;

public interface RestFactory {

    <R> Rest<R> create();

    /**
     * @param clazz Provides the generic type info for the return value
     */
    @SuppressWarnings("unused")
    default <R> Rest<R> create(final Class<R> clazz) {
        return create();
    }

    /**
     * @param typeLiteral Provides the generic type info for the return value. Use it like
     *                    <pre>{@code create(new TypeLiteral<Set<String>>(){})}</pre>
     */
    @SuppressWarnings("unused")
    default <R> Rest<R> create(final TypeLiteral<R> typeLiteral) {
        return create();
    }

    <R> Rest<R> createQuiet();

    /**
     * @param clazz Provides the generic type info for the return value
     */
    @SuppressWarnings("unused")
    default <R> Rest<R> createQuiet(final Class<R> clazz) {
        return createQuiet();
    }

    /**
     * @param typeLiteral Provides the generic type info for the return value. Use it like
     *                    <pre>{@code create(new TypeLiteral<Set<String>>(){})}</pre>
     */
    @SuppressWarnings("unused")
    default <R> Rest<R> createQuiet(final TypeLiteral<R> typeLiteral) {
        return createQuiet();
    }

    String getImportFileURL();
}
