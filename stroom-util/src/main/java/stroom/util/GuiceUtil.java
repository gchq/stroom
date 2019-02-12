package stroom.util;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Types;

import java.util.List;
import java.util.Set;

public class GuiceUtil {

    private GuiceUtil() {
    }

    public static <T> MultiBinderBuilder<T> buildMultiBinder(
            final Binder binder,
            final Class<T> interfaceType) {
        return new MultiBinderBuilder<>(binder, interfaceType);
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeLiteral<Set<T>> setOf(Class<T> type) {
        return (TypeLiteral<Set<T>>)TypeLiteral.get(Types.setOf(type));
    }
    
    public static <T> Set<T> getMultibinderInstance(final Injector injector, final Class<T> type) {
        return injector.getInstance(Key.get(setOf(type)));
    }

    public static class MultiBinderBuilder<T_INTERFACE> {

        private final Multibinder<T_INTERFACE> multibinder;

        MultiBinderBuilder(final Binder binder, final Class<T_INTERFACE> interfaceType) {
            this.multibinder = Multibinder.newSetBinder(binder, interfaceType);
        }

        public MultiBinderBuilder<T_INTERFACE> addBinding(final Class<? extends T_INTERFACE> implementationType) {
            multibinder.addBinding().to(implementationType);
            return this;
        }

        public MultiBinderBuilder<T_INTERFACE> addBindingToInstance(
                final T_INTERFACE implementationInstance) {
            multibinder.addBinding().toInstance(implementationInstance);
            return this;
        }

        public MultiBinderBuilder<T_INTERFACE> addBindings(final List<Class<? extends T_INTERFACE>> implementationTypes) {
            implementationTypes.forEach(implementationType -> {
                multibinder.addBinding().to(implementationType);
            });
            return this;
        }

        public MultiBinderBuilder<T_INTERFACE> addBindings(
                final Class<? extends T_INTERFACE> implementationType1,
                final Class<? extends T_INTERFACE> implementationType2) {

            addBindings(List.of(implementationType1, implementationType2));
            return this;
        }

        public MultiBinderBuilder<T_INTERFACE> addBindings(
                final Class<? extends T_INTERFACE> implementationType1,
                final Class<? extends T_INTERFACE> implementationType2,
                final Class<? extends T_INTERFACE> implementationType3) {

            addBindings(List.of(implementationType1, implementationType2, implementationType3));
            return this;
        }

        public MultiBinderBuilder<T_INTERFACE> addBindings(
                final Class<? extends T_INTERFACE> implementationType1,
                final Class<? extends T_INTERFACE> implementationType2,
                final Class<? extends T_INTERFACE> implementationType3,
                final Class<? extends T_INTERFACE> implementationType4) {

            addBindings(List.of(implementationType1, implementationType2, implementationType3, implementationType4));
            return this;
        }

        public Multibinder<T_INTERFACE> getMultibinder() {
            return multibinder;
        }
    }

}
