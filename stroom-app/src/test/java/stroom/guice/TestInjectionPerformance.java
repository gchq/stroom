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

package stroom.guice;


import stroom.analytics.AnalyticsDataSetup;
import stroom.analytics.impl.TableBuilderAnalyticExecutor;
import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.data.store.api.Store;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.index.VolumeCreator;
import stroom.index.VolumeTestConfigModule;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.selection.VolumeConfig;
import stroom.meta.api.MetaService;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.processor.impl.ProcessorTaskQueueManager;
import stroom.resource.impl.ResourceModule;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.BootstrapTestModule;
import stroom.test.CommonTestControl;
import stroom.test.ContentStoreTestSetup;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.LinkedKeyBinding;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

class TestInjectionPerformance {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestInjectionPerformance.class);

    @Test
    void testPerformance() {
        final Class<?>[] moduleClasses = {
                UriFactoryModule.class,
                CoreModule.class,
                BootstrapTestModule.class,
                ResourceModule.class,
                stroom.cluster.impl.MockClusterModule.class,
                VolumeTestConfigModule.class,
                MockSecurityContextModule.class,
                MockMetaStatisticsModule.class,
                stroom.test.DatabaseTestControlModule.class,
                JerseyModule.class};
        final Module[] instances = new Module[moduleClasses.length];
        for (int i = 0; i < moduleClasses.length; i++) {
            final int pos = i;
            final Runnable r = () -> {
                try {
                    instances[pos] = (Module) moduleClasses[pos].getConstructor().newInstance();
                } catch (final Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            };
            LOGGER.logDurationIfInfoEnabled(r, "Creating " + moduleClasses[i].getSimpleName());
        }


        final Injector injector = LOGGER.logDurationIfInfoEnabled(() ->
                Guice.createInjector(instances), "creating injector");

        final Class<?>[] toInject = {
                ContentStoreTestSetup.class,
                IndexShardManager.class,
                IndexShardWriterCache.class,
                VolumeCreator.class,
                ProcessorTaskQueueManager.class,
                VolumeConfig.class,
                FsVolumeConfig.class,
                FsVolumeService.class,
                PathCreator.class,
                IndexVolumeService.class,

                CommonTestControl.class,
                SecurityContext.class,
                TempDirProvider.class,
                MetaService.class,
                Store.class,
                AnalyticRuleStore.class,
                TableBuilderAnalyticExecutor.class,
                AnalyticsDataSetup.class,
        };

//        createDataSources(injector);

        final Map<Class<?>, Duration> durationMap = new ConcurrentHashMap<>();
        final Set<Class<?>> stackOverflowProtection = new HashSet<>();
        for (final Class<?> clazz : toInject) {
            timeDeepInjection(injector, clazz, durationMap, stackOverflowProtection);
        }

        durationMap.entrySet().stream().sorted(Entry.comparingByValue()).forEach(entry -> {
            LOGGER.info("Created " + entry.getKey().getSimpleName() + " in " + entry.getValue());
        });
    }

    private void createDataSources(final Injector injector) {
        final List<Binding<DataSource>> bindings = injector.findBindingsByType(TypeLiteral.get(DataSource.class));
        final CompletableFuture<?>[] futures = bindings
                .stream()
                .map(binding -> CompletableFuture.runAsync(() -> binding.getProvider().get()))
                .toList()
                .toArray(new CompletableFuture[0]);
        CompletableFuture.allOf(futures).join();
    }

    private void timeDeepInjection(final Injector injector,
                                   final Class<?> clazz,
                                   final Map<Class<?>, Duration> durations,
                                   final Set<Class<?>> stackOverflowProtection) {
        // Prevent stack overflow.
        if (!stackOverflowProtection.contains(clazz)) {
            stackOverflowProtection.add(clazz);

            final Constructor<?> constructor = getInjectConstructor(clazz.getDeclaredConstructors());
            if (constructor != null) {
                final Class<?>[] paramTypes = constructor.getParameterTypes();

                if (paramTypes.length == 0) {
                    timeInjection(injector, clazz, durations);
                } else {
                    for (final Class<?> paramType : paramTypes) {
                        try {
                            final Binding<?> binding = injector.getBinding(paramType);
                            if (binding instanceof final LinkedKeyBinding<?> linkedKeyBinding) {
                                final Key<?> key = linkedKeyBinding.getLinkedKey();
                                final Class<?> rawType = key.getTypeLiteral().getRawType();
                                timeDeepInjection(injector, rawType, durations, stackOverflowProtection);
                            } else {
                                timeDeepInjection(injector, paramType, durations, stackOverflowProtection);
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e::getMessage, e);
                        }
                    }
                    timeInjection(injector, clazz, durations);
                }
            } else {
                timeInjection(injector, clazz, durations);
            }
        }
    }

    private Constructor<?> getInjectConstructor(final Constructor<?>[] constructors) {
        for (final Constructor<?> constructor : constructors) {
            final Inject inject = constructor.getAnnotation(Inject.class);
            if (inject != null) {
                return constructor;
            }
            final jakarta.inject.Inject inject2 = constructor.getAnnotation(jakarta.inject.Inject.class);
            if (inject2 != null) {
                return constructor;
            }
        }
        return null;
    }

    private void timeInjection(final Injector injector, final Class<?> clazz, final Map<Class<?>, Duration> durations) {
        if (!durations.containsKey(clazz)) {
            final long nanos = System.nanoTime();
            injector.getInstance(clazz);
            durations.putIfAbsent(clazz, Duration.ofNanos(System.nanoTime() - nanos));
        }
    }
}
