package stroom.test.common.util.guice;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;

class TestGuiceTestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestGuiceTestUtil.class);

    @Test
    void dumpGuiceModuleHierarchy() {
        final String dump = GuiceTestUtil.dumpGuiceModuleHierarchy(new RootModule());
        LOGGER.info("\n{}", dump);
    }

    @Test
    void dumpBindsSortedByKey() {
        final String dump = GuiceTestUtil.dumpBindsSortedByKey(new RootModule());
        LOGGER.info("\n{}", dump);
    }

    private static class RootModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new ChildModule());
            bind(StandardBind.class).to(StandardBindImpl.class);
            bind(ExplicitSingletonBind.class).asEagerSingleton();

            InterfaceInstanceBind interfaceInstanceBind = new InterfaceInstanceBind() {
            };
            bind(InterfaceInstanceBind.class).toInstance(interfaceInstanceBind);

            ClassInstanceBind classInstanceBind = new ClassInstanceBind();
            bind(ClassInstanceBind.class).toInstance(classInstanceBind);

            bind(ProviderBind.class).toProvider(ProviderBindFactory.class);
            bind(LambdaProviderBind.class).toProvider(() -> new LambdaProviderBind() {

            });

            MapBinder<Integer, MapBinderBind> mapBinder = MapBinder.newMapBinder(
                    binder(), Integer.class, MapBinderBind.class);
            mapBinder.addBinding(1).to(MapBinderBindImpl1.class);

            Multibinder<MultiBinderBind> multiBinder = Multibinder.newSetBinder(
                    binder(), MultiBinderBind.class);
            multiBinder.addBinding().to(MultiBinderBindImpl1.class);
        }
    }

    private static class ChildModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new GrandChildModule());
            bind(StandardBind2.class).to(StandardBind2Impl.class)
                    .asEagerSingleton();

            MapBinder<Integer, MapBinderBind> mapBinder = MapBinder.newMapBinder(
                    binder(), Integer.class, MapBinderBind.class);
            mapBinder.addBinding(2).to(MapBinderBindImpl2.class);

            Multibinder<MultiBinderBind> multiBinder = Multibinder.newSetBinder(
                    binder(), MultiBinderBind.class);
            multiBinder.addBinding().to(MultiBinderBindImpl2.class);
        }
    }

    private static class GrandChildModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(StandardBind3.class).to(StandardBind3Impl.class);

            MapBinder<Integer, MapBinderBind> mapBinder = MapBinder.newMapBinder(
                    binder(), Integer.class, MapBinderBind.class);
            mapBinder.addBinding(3).to(MapBinderBindImpl3.class);

            Multibinder<MultiBinderBind> multiBinder = Multibinder.newSetBinder(
                    binder(), MultiBinderBind.class);
            multiBinder.addBinding().to(MultiBinderBindImpl3.class);
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static interface StandardBind {

    }

    private static class StandardBindImpl implements StandardBind {

        @Inject
        public StandardBindImpl(ImplicitSingletonBind implicitSingletonBind) {
        }
    }

    private static interface InterfaceInstanceBind {

    }

    private static class ClassInstanceBind {

    }

    private static interface ProviderBind {

    }

    private static class ProviderBindFactory implements Provider<ProviderBind> {

        @Override
        public ProviderBind get() {
            return null;
        }
    }

    private static interface LambdaProviderBind {

    }

    @Singleton
    private static class ImplicitSingletonBind {

    }

    private static class ExplicitSingletonBind {

    }

    private static interface MapBinderBind {

    }

    private static class MapBinderBindImpl1 implements MapBinderBind {

    }

    private static class MapBinderBindImpl2 implements MapBinderBind {

    }

    private static class MapBinderBindImpl3 implements MapBinderBind {

    }

    private static interface MultiBinderBind {

    }

    private static class MultiBinderBindImpl1 implements MultiBinderBind {

    }

    private static class MultiBinderBindImpl2 implements MultiBinderBind {

    }

    private static class MultiBinderBindImpl3 implements MultiBinderBind {

    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static interface StandardBind2 {

    }

    private static class StandardBind2Impl implements StandardBind2 {

    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static interface StandardBind3 {

    }

    private static class StandardBind3Impl implements StandardBind3 {

    }

}
