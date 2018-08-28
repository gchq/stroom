package stroom.test;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.YamlUtil;
import stroom.guice.CoreModule;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CoreTestModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreTestModule.class);

    @Override
    protected void configure() {
        // Load dev.yaml
        final String codeSourceLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-integrationtest")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.getParent();
            path = path.resolve("stroom-app");
            path = path.resolve("dev.yml");
        }

        if (path == null) {
            throw new RuntimeException("Unable to find dev.yaml");
        }

        LOGGER.info("Using config from: " + FileUtil.getCanonicalPath(path));

        try (final InputStream inputStream = Files.newInputStream(path)) {
            final AppConfig appConfig = YamlUtil.read(inputStream);
            install(new AppConfigModule(appConfig));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        install(new CoreModule());

        install(new stroom.resource.ResourceModule());
        install(new stroom.cluster.impl.MockClusterModule());
        install(new stroom.node.NodeTestConfigModule());
        install(new stroom.security.impl.mock.MockSecurityContextModule());
        install(new stroom.statistics.internal.MockInternalStatisticsModule());
        install(new stroom.streamtask.statistic.MockMetaDataStatisticModule());
        install(new stroom.test.DatabaseTestControlModule());
    }
}
