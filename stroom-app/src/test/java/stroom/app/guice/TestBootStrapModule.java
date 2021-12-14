package stroom.app.guice;

import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.Config;
import stroom.config.global.impl.ConfigMapper;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.guice.GuiceUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import javax.sql.DataSource;

class TestBootStrapModule {

    @Test
    void test() throws IOException {

        final Path configFile = Path.of("Dummy");
//        final Config config = StroomYamlUtil.readConfig(configFile);
        final Config config = new Config(new AppConfig());

        final BootStrapModule bootstrapModule = new BootStrapModule(
                config,
                null,
                configFile,
                DbTestModule::new,
                AppConfigModule::new);

        final Injector injector = Guice.createInjector(bootstrapModule);

        // ensure we can inject all the datasources
        final Set<DataSource> dataSources = injector.getInstance(Key.get(GuiceUtil.setOf(DataSource.class)));

        final ConfigMapper configMapper = injector.getInstance(ConfigMapper.class);

        final AppConfig appConfig = configMapper.getConfigObject(AppConfig.class);

        Assertions.assertThat(appConfig)
                .isNotNull();
    }
}
