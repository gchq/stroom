package stroom.legacy.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.util.HasHealthCheck;

import com.codahale.metrics.health.HealthCheck;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

@Deprecated
public class DbHealthCheck implements HasHealthCheck {

    private final LegacyDbConnProvider legacyDbConnProvider;
    private final LegacyDbConfig legacyDbConfig;
    private final CommonDbConfig commonDbConfig;

    @Inject
    public DbHealthCheck(final LegacyDbConnProvider legacyDbConnProvider,
                         final LegacyDbConfig legacyDbConfig,
                         final CommonDbConfig commonDbConfig) {
        this.legacyDbConnProvider = legacyDbConnProvider;
        this.legacyDbConfig = legacyDbConfig;
        this.commonDbConfig = commonDbConfig;
    }

    @Override
    public HealthCheck.Result getHealth() {
        final ConnectionConfig coreConnectionConfig = legacyDbConfig.getDbConfig().getConnectionConfig();
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

        try (Connection connection = legacyDbConnProvider.getConnection()){
            builder.healthy();
        } catch (RuntimeException | SQLException e) {
            builder.unhealthy(e);
        }
        return builder.build();
    }
}
