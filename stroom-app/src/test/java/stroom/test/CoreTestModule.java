package stroom.test;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.app.guice.CoreModule;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.YamlUtil;
import stroom.db.util.DbModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.TestDbModule;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CoreTestModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreTestModule.class);

    private Path yamlConfigPath;
    private AppConfig appConfig;

    public CoreTestModule() {
    }

    public CoreTestModule(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

//    private CoreTestModule(final AppConfig appConfig, final AbstractModule dbModule) {
//        this.appConfig = appConfig;
//        install(dbModule);
//    }
//
//    public static CoreTestModule withRegularDb(final AppConfig appConfig) {
//        return new CoreTestModule(appConfig, new DbModule());
//    }
//
//    public static CoreTestModule withRegularDb() {
//        return new CoreTestModule(null, new DbModule());
//    }
//
//    public static CoreTestModule withEmbeddedTestDb(final AppConfig appConfig) {
//        return new CoreTestModule(appConfig, new TestDbModule());
//    }
//
//    public static CoreTestModule withEmbeddedTestDb() {
//        return new CoreTestModule(null, new TestDbModule());
//    }

    @Override
    protected void configure() {

        if (appConfig == null) {
            appConfig = getLocalAppConfig();
        } else {
            LOGGER.info("Using supplied AppConfig object");
            yamlConfigPath = Paths.get("DUMMY");
        }

        install(new AppConfigModule(appConfig, yamlConfigPath));
        install(new CoreModule());
        install(new ResourceModule());
        install(new stroom.cluster.impl.MockClusterModule());
        install(new VolumeTestConfigModule());
        install(new MockSecurityContextModule());
        install(new MockMetaStatisticsModule());
        install(new stroom.test.DatabaseTestControlModule());
    }

    private AppConfig getLocalAppConfig() {
        final AppConfig appConfig;// Load dev.yaml
        final String codeSourceLocation = CoreTestModule.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-app")) {
            path = path.getParent();
        }

        // resolve local.yml in the root of the repo
        if (path != null) {
            path = path.getParent();
            path = path.resolve("local.yml");
        }

        if (path == null) {
            throw new RuntimeException("Unable to find local.yml, try running local.yml.sh in the root of the repo " +
                    "to create one.");
        }

        LOGGER.info("Using config from: " + FileUtil.getCanonicalPath(path));

        this.yamlConfigPath = path;

        try {
            appConfig = YamlUtil.readAppConfig(path);
        } catch (final IOException e) {
            throw new UncheckedIOException("Error opening local.yml, try running local.yml.sh in the root of " +
                    "the repo to create one.", e);
        }
        return appConfig;
    }
}
