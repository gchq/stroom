package stroom.util.guice;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Types;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GuiceUtil {

    private GuiceUtil() {
    }

    public static <T> MultiBinderBuilder<T> buildMultiBinder(
            final Binder binder,
            final Class<T> interfaceType) {
        return new MultiBinderBuilder<>(binder, interfaceType);
    }

    public static <K, V> MapBinderBuilder<K, V> buildMapBinder(
            final Binder binder,
            final Class<K> keyType,
            final Class<V> valueType) {
        return new MapBinderBuilder<>(binder, keyType, valueType);
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeLiteral<Set<T>> setOf(Class<T> type) {
        return (TypeLiteral<Set<T>>) TypeLiteral.get(Types.setOf(type));
    }

    @SuppressWarnings("unchecked")
    public static <K, V> TypeLiteral<Map<K, V>> mapOf(Class<K> keyType, Class<V> valueType) {
        return (TypeLiteral<Map<K, V>>) TypeLiteral.get(Types.mapOf(keyType, valueType));
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeLiteral<List<T>> listOf(Class<T> type) {
        return (TypeLiteral<List<T>>) TypeLiteral.get(Types.listOf(type));
    }


    // --------------------------------------------------------------------------------


    public static class MultiBinderBuilder<T_INTERFACE> {

        private final Multibinder<T_INTERFACE> multibinder;

        MultiBinderBuilder(final Binder binder, final Class<T_INTERFACE> interfaceType) {
            this.multibinder = Multibinder.newSetBinder(binder, interfaceType);
        }

        @SuppressWarnings("unchecked")
        public MultiBinderBuilder<T_INTERFACE> addBinding(final Class<? extends T_INTERFACE> implementationType) {
            return addBindings(implementationType);
        }

        public MultiBinderBuilder<T_INTERFACE> addBindings(final Class<? extends T_INTERFACE>... implementationTypes) {
            Arrays.stream(implementationTypes)
                    .forEach(implementationType -> multibinder.addBinding().to(implementationType));
            return this;
        }

        public Multibinder<T_INTERFACE> getMultibinder() {
            return multibinder;
        }
    }


    // --------------------------------------------------------------------------------


    public static class MapBinderBuilder<K, V> {

        private final MapBinder<K, V> mapBinder;

        MapBinderBuilder(final Binder binder,
                         final Class<K> keyType,
                         final Class<V> valueType) {
            this.mapBinder = MapBinder.newMapBinder(binder, keyType, valueType);
        }

        public MapBinderBuilder<K, V> addBinding(
                final K key,
                final Class<? extends V> valueType) {
            mapBinder.addBinding(key).to(valueType);
            return this;
        }

        public MapBinderBuilder<K, V> addProviderBinding(
                final K key,
                final Class<? extends Provider<? extends V>> valueProviderType) {
            mapBinder.addBinding(key).toProvider(valueProviderType);
            return this;
        }

        public MapBinder<K, V> getMapBinder() {
            return mapBinder;
        }
    }
}
