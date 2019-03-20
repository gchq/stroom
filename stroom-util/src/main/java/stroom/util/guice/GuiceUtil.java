package stroom.util.guice;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

import java.util.Arrays;

public class GuiceUtil {
    private GuiceUtil() {
    }

    public static <T> MultiBinderBuilder<T> buildMultiBinder(
            final Binder binder,
            final Class<T> interfaceType) {
        return new MultiBinderBuilder<>(binder, interfaceType);
    }

    public static class MultiBinderBuilder<T_INTERFACE> {

        private final Multibinder<T_INTERFACE> multibinder;

        MultiBinderBuilder(final Binder binder, final Class<T_INTERFACE> interfaceType) {
            this.multibinder = Multibinder.newSetBinder(binder, interfaceType);
        }

        public MultiBinderBuilder<T_INTERFACE> addBinding(final Class<? extends T_INTERFACE> implementationType) {
            return addBindings(implementationType);
        }

        public MultiBinderBuilder<T_INTERFACE> addBindings(final Class<? extends T_INTERFACE>... implementationTypes) {
            Arrays.stream(implementationTypes).forEach(implementationType -> multibinder.addBinding().to(implementationType));
            return this;
        }

        public Multibinder<T_INTERFACE> getMultibinder() {
            return multibinder;
        }
    }

}
