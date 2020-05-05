package stroom.core.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.util.HasHealthCheck;

import com.codahale.metrics.health.HealthCheck;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class DbHealthCheck implements HasHealthCheck {

    private final CoreDbConnProvider coreDbConnProvider;
    private final CoreConfig coreConfig;
    private final CommonDbConfig commonDbConfig;

    @Inject
    public DbHealthCheck(final CoreDbConnProvider coreDbConnProvider,
                         final CoreConfig coreConfig,
                         final CommonDbConfig commonDbConfig) {
        this.coreDbConnProvider = coreDbConnProvider;
        this.coreConfig = coreConfig;
        this.commonDbConfig = commonDbConfig;
    }

    @Override
    public HealthCheck.Result getHealth() {
        final ConnectionConfig coreConnectionConfig = coreConfig.getDbConfig().getConnectionConfig();
        final ConnectionConfig commonConnectionConfig = commonDbConfig.getConnectionConfig();

        final HealthCheck.ResultBuilder builder = HealthCheck.Result.builder()
                .withDetail(
                        "jdbcUrl",
                        Optional.ofNullable(commonConnectionConfig.getJdbcDriverUrl())
                                .orElse(coreConnectionConfig.getJdbcDriverUrl()))
                .withDetail(
                        "username",
                        Optional.ofNullable(commonConnectionConfig.getJdbcDriverUsername())
                                .orElse(coreConnectionConfig.getJdbcDriverUsername()));

        try (Connection connection = coreDbConnProvider.getConnection()){
            builder.healthy();
        } catch (RuntimeException | SQLException e) {
            builder.unhealthy(e);
        }
        return builder.build();
    }
}
