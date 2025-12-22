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

package stroom.proxy.app;

import stroom.proxy.app.guice.ProxyModule;
import stroom.util.logging.LogUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.function.Consumer;

public class TestProxyGuiceBindings extends AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyGuiceBindings.class);

    @Test
    public void testAllGuiceBinds() {
        final Injector injector = ((MyApp) getDropwizard().getApplication()).getInjector();

        // Test all the constructors to make sure guice can bind them
        // As proxy shares classes with stroom we have no way of knowing which shared classes are used
        // by proxy, so we have to make do with just checking all the non-shared classes.
        findConstructors(injector::getProvider, "stroom.proxy", jakarta.inject.Inject.class);
        findConstructors(injector::getProvider, "stroom.proxy", com.google.inject.Inject.class);
    }

    private void findConstructors(final Consumer<Class<?>> actionPerClass,
                                  final String packagePrefix,
                                  final Class<? extends Annotation> annotationClass) {
        LOGGER.info("Finding all classes in {} with {} constructors",
                packagePrefix, annotationClass.getCanonicalName());

        final ScanResult scanResult = new ClassGraph()
                .acceptPackages(packagePrefix)
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan();

        scanResult.getClassesWithMethodAnnotation(annotationClass.getName())
                .forEach(classInfo -> {
                    final Class<?> clazz = classInfo.loadClass();
                    LOGGER.info("  Testing injection for " + clazz.getCanonicalName());
                    try {
                        actionPerClass.accept(clazz);
                    } catch (final Exception e) {
                        // TODO At the moment we can only log an error and not fail the test as not all
                        //   visible classes are meant to be injectable. Leaving this test here in  case
                        //   this changes.
                        Assertions.fail(LogUtil.message(
                                "Unable to get instance of {} due to; ", clazz.getCanonicalName()), e);
                        LOGGER.error("    Unable to get instance of {} due to; ", clazz.getCanonicalName(), e);
                    }
                });
    }

    @Override
    protected Class<? extends Application<Config>> getAppClass() {
        return MyApp.class;
    }


    // --------------------------------------------------------------------------------


    public static class MyApp extends Application<Config> {

        private static final Logger LOGGER = LoggerFactory.getLogger(MyApp.class);

        private Injector injector;

        public MyApp() {
        }

        @Override
        public void run(final Config configuration, final Environment environment) throws Exception {
            LOGGER.info("Here");

            final ProxyModule proxyModule = new ProxyModule(
                    configuration,
                    environment,
                    Path.of("dummy/path/to/config.yml"));
            injector = Guice.createInjector(proxyModule);
        }

        public Injector getInjector() {
            return injector;
        }
    }
}
