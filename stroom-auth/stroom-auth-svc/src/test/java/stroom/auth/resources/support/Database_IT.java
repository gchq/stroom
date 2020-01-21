package stroom.auth.resources.support;

import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.testcontainers.containers.MySQLContainer;
import stroom.auth.AuthDbConnProvider;
import stroom.auth.TestAuthDbConnProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static stroom.auth.db.Tables.TOKENS;
import static stroom.auth.db.Tables.TOKEN_TYPES;
import static stroom.auth.db.Tables.USERS;

/**
 * A belt and braces approach to cleaning the database. It's not an expensive operation and doing it before
 * and after a test guarantees we won't run into issues with existing data.
 */
public abstract class Database_IT {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Database_IT.class);

    public static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
    public static final String DATABASE_NAME = "auth";
    public static final String JDBC_USER = "authuser";
    public static final String JDBC_PASSWORD = "stroompassword1";

    private static final String MYSQL_DOCKER_IMAGE = "mysql:5.6.43";

    protected AuthDbConnProvider authDbConnProvider;

    @ClassRule
    public static MySQLContainer mysql = new MySQLContainer(MYSQL_DOCKER_IMAGE)
            .withDatabaseName(DATABASE_NAME)
            .withUsername(JDBC_USER)
            .withPassword(JDBC_PASSWORD);

    @After
    public void after(){
        cleanDatabase();
    }

    @Before
    public void before() {
        authDbConnProvider = new TestAuthDbConnProvider(mysql);
        Map<String, String> flywayConfiguration = new HashMap<String, String>();
        flywayConfiguration.put("flyway.driver", JDBC_DRIVER);
        flywayConfiguration.put("flyway.url", mysql.getJdbcUrl());
        flywayConfiguration.put("flyway.user", JDBC_USER);
        flywayConfiguration.put("flyway.password", JDBC_PASSWORD);
        Flyway flyway = new Flyway();
        flyway.configure(flywayConfiguration);

        // Start the migration
        flyway.migrate();

        cleanDatabase();
    }

    private void cleanDatabase(){
        final String jdbcUrl = mysql.getJdbcUrl();
        LOGGER.info("The JDBC URL is {}", jdbcUrl);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, JDBC_USER, JDBC_PASSWORD)) {
            DSLContext database = DSL.using(conn, SQLDialect.MYSQL);

            // Delete non-admin users
            database.deleteFrom(USERS).where(USERS.EMAIL.ne("admin")).execute();
            Integer adminUserId = database
                    .select(USERS.ID)
                    .from(USERS)
                    .where(USERS.EMAIL.eq("admin"))
                    .fetchOne()
                    .into(Integer.class);

            Integer apiTokenTypeID = database
                    .select(TOKEN_TYPES.ID)
                    .from(TOKEN_TYPES)
                    .where(TOKEN_TYPES.TOKEN_TYPE.eq("api"))
                    .fetchOne()
                    .into(Integer.class);

            database.deleteFrom(TOKENS).execute();

            database.insertInto(TOKENS)
                    // This is the long-lived token from Flyway
                    .set(TOKENS.TOKEN,
                            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlzcyI6InN0cm9vbSJ9.NLTH0YNedtKsco0E6jWTcPYV3AW2mLlgLf5TVxXVa-I")
                    .set(TOKENS.TOKEN_TYPE_ID, apiTokenTypeID)
                    .set(TOKENS.USER_ID, adminUserId)
                    .set(TOKENS.ISSUED_ON, Timestamp.from(Instant.now()))
                    .execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
