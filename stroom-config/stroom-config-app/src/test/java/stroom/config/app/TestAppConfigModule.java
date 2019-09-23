package stroom.config.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.cglib.core.$DebuggingClassWriter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.activity.impl.db.ActivityConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.HasDbConfig;
import stroom.task.api.TaskManager;
import stroom.util.io.FileUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TestAppConfigModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppConfigModule.class);
    private static final String MODIFIED_JDBC_DRIVER = "modified.jdbc.driver";

    private final Path tmpDir = FileUtil.createTempDir(this.getClass().getSimpleName());

    @AfterEach
    void afterEach() {
        FileUtil.deleteContents(tmpDir);
    }

    @Test
    void configure() throws IOException {

        Path devYamlPath = TestYamlUtil.getDevYamlPath();

        LOGGER.debug("dev yaml path: {}", devYamlPath.toAbsolutePath());


        LOGGER.debug("tempDir path: {}", devYamlPath.toAbsolutePath());

        Path devYamlCopyPath = tmpDir.resolve(devYamlPath.getFileName());

        // Make a copy of dev.yml so we can hack about with it
        Files.copy(devYamlPath, devYamlCopyPath);

        //        // Modify our copy file so its
        String ymlContent = Files.readString(devYamlCopyPath);
        ymlContent = ymlContent.replaceAll(
                "(\\$\\{STROOM_JDBC_DRIVER_CLASS_NAME:-)?com.mysql.cj.jdbc.Driver(}?)",
                MODIFIED_JDBC_DRIVER);
        Files.writeString(devYamlCopyPath, ymlContent, StandardCharsets.UTF_8);

        Injector injector = Guice.createInjector(new AppConfigModule(devYamlCopyPath));

        AppConfig appConfig = injector.getInstance(AppConfig.class);
        CommonDbConfig commonDbConfig = injector.getInstance(CommonDbConfig.class);

        Assertions.assertThat(commonDbConfig.getConnectionConfig().getJdbcDriverClassName())
                .isEqualTo(MODIFIED_JDBC_DRIVER);

        // Make sure all the getters that return a HasDbConfig have the modified conn value
        Arrays.stream(appConfig.getClass().getDeclaredMethods())
                .filter(method -> method.getName().startsWith("get"))
                .filter(method -> HasDbConfig.class.isAssignableFrom(method.getReturnType()))
                .map(method -> {
                    try {
                        return (HasDbConfig) method.invoke(appConfig);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                })
                .forEach(hasDbConfig -> {
                    Assertions.assertThat(hasDbConfig.getDbConfig().getConnectionConfig())
                            .isEqualTo(commonDbConfig.getConnectionConfig());
                    Assertions.assertThat(hasDbConfig.getDbConfig().getConnectionConfig().getJdbcDriverClassName())
                            .contains(MODIFIED_JDBC_DRIVER);
                    Assertions.assertThat(hasDbConfig.getDbConfig().getConnectionPoolConfig())
                            .isEqualTo(commonDbConfig.getConnectionPoolConfig());
                });
    }
}