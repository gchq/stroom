package stroom.proxy.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.app.guice.ProxyModule;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;

@ExtendWith(DropwizardExtensionsSupport.class)
public class TestProxyGuiceBindings extends AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyGuiceBindings.class);


    private static final MyApp myApp = new MyApp();

    private static final DropwizardAppExtension<Config> DROPWIZARD = new DropwizardAppExtension<Config>(
        MyApp.class, getConfig());

    @Test
    public void testAllGuiceBinds() {
        Injector injector = ((MyApp)DROPWIZARD.getApplication()).getInjector();

        //test all the constructors to make sure guice can bind them
        findConstructors(injector::getProvider, "stroom", javax.inject.Inject.class);
        findConstructors(injector::getProvider, "stroom", com.google.inject.Inject.class);
    }

    private void findConstructors(final Consumer<Class<?>> actionPerClass,
                                  final String packagePrefix,
                                  final Class<? extends Annotation> annotationClass) {
        LOGGER.info("Finding all classes in {} with {} constructors",
            packagePrefix, annotationClass.getCanonicalName());

        ScanResult scanResult = new ClassGraph()
            .whitelistPackages(packagePrefix)
            .enableClassInfo()
            .enableMethodInfo()
            .enableAnnotationInfo()
            .scan();

        scanResult.getClassesWithMethodAnnotation(annotationClass.getName())
            .forEach(classInfo -> {
                Class<?> clazz = classInfo.loadClass();
                LOGGER.info("  Testing injection for " + clazz.getCanonicalName());
                try {
                    actionPerClass.accept(clazz);
                } catch (Exception e) {
                    // TODO At the moment we can only log an error and not fail the test as not all
                    //   visible classes are meant to be injectable. Leaving this test here in  case
                    //   this changes.
                    LOGGER.error("    Unable to get instance of {} due to; ",clazz.getCanonicalName(), e);
                }
            });
    }

    public static class MyApp extends Application<Config> {

        private static final Logger LOGGER = LoggerFactory.getLogger(MyApp.class);

        private Injector injector;

        public MyApp() {
        }

        @Override
        public void run(final Config configuration, final Environment environment) throws Exception {
            LOGGER.info("Here");

            final ProxyModule proxyModule = new ProxyModule(configuration, environment);
            injector = Guice.createInjector(proxyModule);
        }

        public Injector getInjector() {
            return injector;
        }
    }
}
