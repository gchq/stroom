package stroom.security.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.SecurityConfig;
import stroom.security.impl.UserDao;

import java.util.function.Function;

public class SecurityDbModule extends AbstractFlyWayDbModule<SecurityConfig, SecurityDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityDbModule.class);
    private static final String MODULE = "stroom-security";
    private static final String FLYWAY_LOCATIONS = "stroom/security/impl/db/migration";
    private static final String FLYWAY_TABLE = "security_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(UserDao.class).to(UserDaoImpl.class);
        bind(DocumentPermissionDao.class).to(DocumentPermissionDaoImpl.class);
        bind(AppPermissionDao.class).to(AppPermissionDaoImpl.class);
    }

    @Override
    public String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    public String getModuleName() {
        return MODULE;
    }

    @Override
    public String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    public Function<HikariConfig, SecurityDbConnProvider> getConnectionProviderConstructor() {
        return SecurityDbConnProvider::new;
    }

    @Override
    public Class<SecurityDbConnProvider> getConnectionProviderType() {
        return SecurityDbConnProvider.class;
    }
}
