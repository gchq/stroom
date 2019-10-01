package stroom.test.common.util.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;

public class DbTestUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbTestUtil.class);

    private static final String TESTCONTAINERS_JDBC_CONTAINER_DATABASE_DRIVER = "org.testcontainers.jdbc.ContainerDatabaseDriver";

    private DbTestUtil() {
    }

    public static void applyTestContainersConfig(final ConnectionConfig connectionConfig) {
        try {
            final Class<?> clazz = Class.forName(TESTCONTAINERS_JDBC_CONTAINER_DATABASE_DRIVER);
            if (clazz != null) {
                LOGGER.info("Using test container DB connection config");

                connectionConfig.setJdbcDriverClassName("com.mysql.cj.jdbc.Driver");
                connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:5.5.52://localhost:3306/test");
//                    connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:5.6.43://localhost:3306/test");
//                    connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:5.7.25://localhost:3306/test");
//                    connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:8.0.15://localhost:3306/test");
                connectionConfig.setJdbcDriverPassword("test");
                connectionConfig.setJdbcDriverUsername("test");

            }
        } catch (final ClassNotFoundException e) {
            LOGGER.debug("Can't find class {} for Test Containers, falling back to standard DB connection",
                    TESTCONTAINERS_JDBC_CONTAINER_DATABASE_DRIVER);
        }
    }
}
