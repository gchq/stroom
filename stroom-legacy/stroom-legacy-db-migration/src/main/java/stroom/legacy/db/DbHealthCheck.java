package stroom.legacy.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.util.HasHealthCheck;

import com.codahale.metrics.health.HealthCheck;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import javax.inject.Inject;

@Deprecated
public class DbHealthCheck implements HasHealthCheck {

    private final LegacyDbConnProvider legacyDbConnProvider;
    private final LegacyConfig legacyConfig;
    private final CommonDbConfig commonDbConfig;

    @Inject
    public DbHealthCheck(final LegacyDbConnProvider legacyDbConnProvider,
                         final LegacyConfig legacyConfig,
                         final CommonDbConfig commonDbConfig) {
        this.legacyDbConnProvider = legacyDbConnProvider;
        this.legacyConfig = legacyConfig;
        this.commonDbConfig = commonDbConfig;
    }

    @Override
    public HealthCheck.Result getHealth() {
        final ConnectionConfig coreConnectionConfig = legacyConfig.getDbConfig().getConnectionConfig();
        final ConnectionConfig commonConnectionConfig = commonDbConfig.getConnectionConfig();

        final HealthCheck.ResultBuilder builder = HealthCheck.Result.builder()
                .withDetail(
                        "jdbcUrl",
                        Optional.ofNullable(commonConnectionConfig.getUrl())
                                .orElse(coreConnectionConfig.getUrl()))
                .withDetail(
                        "username",
                        Optional.ofNullable(commonConnectionConfig.getUser())
                                .orElse(coreConnectionConfig.getUser()));

        try (Connection connection = legacyDbConnProvider.getConnection()) {
            builder.healthy();
        } catch (RuntimeException | SQLException e) {
            builder.unhealthy(e);
        }
        return builder.build();
    }
}
