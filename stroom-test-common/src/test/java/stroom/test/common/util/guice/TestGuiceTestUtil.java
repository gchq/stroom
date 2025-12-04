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

package stroom.test.common.util.guice;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

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

            final InterfaceInstanceBind interfaceInstanceBind = new InterfaceInstanceBind() {
            };
            bind(InterfaceInstanceBind.class).toInstance(interfaceInstanceBind);

            final ClassInstanceBind classInstanceBind = new ClassInstanceBind();
            bind(ClassInstanceBind.class).toInstance(classInstanceBind);

            bind(ProviderBind.class).toProvider(ProviderBindFactory.class);
            bind(LambdaProviderBind.class).toProvider(() -> new LambdaProviderBind() {

            });

            final MapBinder<Integer, MapBinderBind> mapBinder = MapBinder.newMapBinder(
                    binder(), Integer.class, MapBinderBind.class);
            mapBinder.addBinding(1).to(MapBinderBindImpl1.class);

            final Multibinder<MultiBinderBind> multiBinder = Multibinder.newSetBinder(
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

            final MapBinder<Integer, MapBinderBind> mapBinder = MapBinder.newMapBinder(
                    binder(), Integer.class, MapBinderBind.class);
            mapBinder.addBinding(2).to(MapBinderBindImpl2.class);

            final Multibinder<MultiBinderBind> multiBinder = Multibinder.newSetBinder(
                    binder(), MultiBinderBind.class);
            multiBinder.addBinding().to(MultiBinderBindImpl2.class);
        }
    }

    private static class GrandChildModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(StandardBind3.class).to(StandardBind3Impl.class);

            final MapBinder<Integer, MapBinderBind> mapBinder = MapBinder.newMapBinder(
                    binder(), Integer.class, MapBinderBind.class);
            mapBinder.addBinding(3).to(MapBinderBindImpl3.class);

            final Multibinder<MultiBinderBind> multiBinder = Multibinder.newSetBinder(
                    binder(), MultiBinderBind.class);
            multiBinder.addBinding().to(MultiBinderBindImpl3.class);
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static interface StandardBind {

    }

    private static class StandardBindImpl implements StandardBind {

        @Inject
        public StandardBindImpl(final ImplicitSingletonBind implicitSingletonBind) {
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
