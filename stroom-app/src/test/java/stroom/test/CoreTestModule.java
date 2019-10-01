package stroom.test;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.app.guice.CoreModule;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.YamlUtil;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CoreTestModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreTestModule.class);

    private Path yamlConfigPath = null;
    private boolean useTestContainers = true;

    public CoreTestModule(final boolean useTestContainers) {
        this.useTestContainers = useTestContainers;
    }

    public CoreTestModule(final Path yamlConfigPath) {
        this.yamlConfigPath = yamlConfigPath;
    }

    @Override
    protected void configure() {
        Path path;

        if (yamlConfigPath != null) {
            path = yamlConfigPath;
        } else {
            // Load dev.yaml
            final String codeSourceLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();

            path = Paths.get(codeSourceLocation);
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
        }

        LOGGER.info("Using config from: " + FileUtil.getCanonicalPath(path));

        AppConfig appConfig;

        try  {
            appConfig = YamlUtil.readAppConfig(path);
        } catch (final IOException e) {
            throw new UncheckedIOException("Error opening local.yml, try running local.yml.sh in the root of " +
                    "the repo to create one.", e);
        }

        if (useTestContainers) {
            LOGGER.info("Setting up Test Containers DB config");
            // By decorating the common config it should be applied to all DB conns
            DbTestUtil.applyTestContainersConfig(appConfig.getCommonDbConfig().getConnectionConfig());
        } else {
            LOGGER.info("Not using test container DB connection config");
        }

        install(new AppConfigModule(appConfig, path));
        install(new CoreModule());
        install(new ResourceModule());
        install(new stroom.cluster.impl.MockClusterModule());
        install(new VolumeTestConfigModule());
        install(new MockSecurityContextModule());
        install(new MockMetaStatisticsModule());
        install(new stroom.test.DatabaseTestControlModule());
    }

}
