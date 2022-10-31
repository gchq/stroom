package stroom.app.guice;

import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.guice.GuiceTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import javax.validation.constraints.NotNull;

class TestAppModule extends AbstractCoreIntegrationTest {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAppModule.class);

    @Disabled // manual test to dump the guice modules/bindings tree. Needs a db connection
    @Test
    void dumpAppModuleTree() {
        final BootStrapModule bootStrapModule = getBootStrapModule();

        final String bootStrapDump = GuiceTestUtil.dumpGuiceModuleHierarchy(bootStrapModule);
        LOGGER.info("bootStrap:\n{}", bootStrapDump);

        // Stroom use the bootstrap module's injector to create a child injector with the AppModule
        final AbstractModule combinedModule = getCombinedModule(bootStrapModule);
        final String appDump = GuiceTestUtil.dumpGuiceModuleHierarchy(combinedModule);
        LOGGER.info("app:\n{}", appDump);
    }

    @Disabled // manual test to dump the guice binds sorted by key
    @Test
    void dumpAppModuleBindKeys() {
        final BootStrapModule bootStrapModule = getBootStrapModule();
        final String bootStrapDump = GuiceTestUtil.dumpBindsSortedByKey(bootStrapModule);
        LOGGER.info("bootStrap:\n{}", bootStrapDump);

        final AbstractModule combinedModule = getCombinedModule(bootStrapModule);

        final String appDump = GuiceTestUtil.dumpBindsSortedByKey(combinedModule);
        LOGGER.info("app:\n{}", appDump);
    }

    @NotNull
    private BootStrapModule getBootStrapModule() {
        final Config config = new Config(new AppConfig());
        final BootStrapModule bootStrapModule = new BootStrapModule(config, Path.of("DUMMY"));
        return bootStrapModule;
    }

    @NotNull
    private AbstractModule getCombinedModule(final BootStrapModule bootStrapModule) {
        // Stroom use the bootstrap module's injector to create a child injector with the AppModule
        final AppModule appModule = new AppModule();
        return new AbstractModule() {
            @Override
            protected void configure() {
                install(bootStrapModule);
                install(appModule);
            }
        };
    }
}
