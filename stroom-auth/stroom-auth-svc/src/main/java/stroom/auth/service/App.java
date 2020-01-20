/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.auth.service;

import com.bendb.dropwizard.jooq.JooqBundle;
import com.bendb.dropwizard.jooq.JooqFactory;
import com.github.toastshaman.dropwizard.auth.jwt.JwtAuthFilter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider.Binder;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.flyway.FlywayBundle;
import io.dropwizard.flyway.FlywayFactory;
import io.dropwizard.jersey.sessions.SessionFactoryProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.flywaydb.core.Flyway;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.jooq.Configuration;
import org.jose4j.jwt.consumer.JwtConsumer;
import stroom.auth.EmailSender;
import stroom.auth.PasswordIntegrityCheckTask;
import stroom.auth.TokenVerifier;
import stroom.auth.config.Config;
import stroom.auth.exceptions.mappers.BadRequestExceptionMapper;
import stroom.auth.exceptions.mappers.NoSuchUserExceptionMapper;
import stroom.auth.exceptions.mappers.TokenCreationExceptionMapper;
import stroom.auth.exceptions.mappers.UnsupportedFilterExceptionMapper;
import stroom.auth.resources.authentication.v1.AuthenticationResource;
import stroom.auth.resources.token.v1.TokenResource;
import stroom.auth.resources.user.v1.UserResource;
import stroom.auth.service.security.ServiceUser;
import stroom.auth.service.security.UserAuthenticator;
import stroom.auth.util.db.DbUtil;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.SessionCookieConfig;
import java.util.EnumSet;
import java.util.Timer;

public final class App extends Application<Config> {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(App.class);

    public static final String SESSION_COOKIE_NAME = "authSessionId";

    private Injector injector;

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    private final JooqBundle jooqBundle = new JooqBundle<Config>() {
        public DataSourceFactory getDataSourceFactory(Config configuration) {
            return configuration.getDataSourceFactory();
        }

        public JooqFactory getJooqFactory(Config configuration) {
            return configuration.getJooqFactory();
        }
    };

    private final FlywayBundle flywayBundle = new FlywayBundle<Config>() {
        public DataSourceFactory getDataSourceFactory(Config config) {
            return config.getDataSourceFactory();
        }

        public FlywayFactory getFlywayFactory(Config config) {
            return config.getFlywayFactory();
        }
    };

    @Override
    public void run(Config config, Environment environment) throws Exception {

        // We want to be resilient against the database not being available, so we'll keep trying to migrate if there's
        // an exception. This approach blocks the startup of the service until the database is available. The downside
        // of this is that the admin pages won't be available - any future dashboarding that wants to emit information
        // about the missing database won't be able to do so. The upside of this approach is that it's very simple
        // to implement from where we are now, i.e. we don't need to add service-wide code to handle a missing database
        // e.g. in JwkDao.init().
        waitForDatabaseConnection(config);

        // The first thing to do is set up Guice
        Configuration jooqConfig = this.jooqBundle.getConfiguration();

        // We only have one schema so don't qualify tables with their schema else we can't
        // use a different db name in the connection url
        jooqConfig.settings().setRenderSchema(false);

        injector = Guice.createInjector(new stroom.auth.service.Module(config, jooqConfig));

        // We need the database before we need most other things
        migrate(config, environment);

        // And we want to configure authentication before the resources
        configureAuthentication(injector.getInstance(TokenVerifier.class).getJwtConsumer(), environment);

        // Now we can configure everything else
        registerResources(environment);
        registerExceptionMappers(environment);
        registerHealthChecks(environment, injector.getInstance(EmailSender.class), config);
        configureSessionHandling(environment);
        configureCors(environment);
        schedulePasswordChecks(config, injector.getInstance(PasswordIntegrityCheckTask.class));

        // Ensure the session cookie that provides JSESSIONID is secure.
        final SessionCookieConfig sessionCookieConfig = environment
                .getApplicationContext()
                .getServletContext()
                .getSessionCookieConfig();
        sessionCookieConfig.setSecure(true);
        sessionCookieConfig.setHttpOnly(true);
    }

    private void waitForDatabaseConnection(final Config config) {
        DataSourceFactory dataSourceFactory = config.getDataSourceFactory();

        final String driverClassname = dataSourceFactory.getDriverClass();
        final String driverUrl = dataSourceFactory.getUrl();
        final String driverUsername = dataSourceFactory.getUser();
        final String driverPassword = dataSourceFactory.getPassword();

        boolean didConnect = DbUtil.waitForConnection(driverClassname, driverUrl, driverUsername, driverPassword);

        if (!didConnect) {
            LOGGER.error("Can't connect to the databases, shutting down");
            System.exit(1);
        }
    }

    private static void configureSessionHandling(Environment environment) {
        SessionHandler sessionHandler = new SessionHandler();
        // We need to give our session cookie a name other than JSESSIONID, otherwise it might
        // clash with other services running on the same domain.
        sessionHandler.setSessionCookie(SESSION_COOKIE_NAME);
        environment.servlets().setSessionHandler(sessionHandler);
        environment.jersey().register(SessionFactoryProvider.class);
    }

    public void initialize(Bootstrap bootstrap) {
        // This allows us to use templating in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));
        bootstrap.addBundle(this.jooqBundle);
        bootstrap.addBundle(this.flywayBundle);
    }

    private void registerResources(Environment environment) {
        environment.jersey().register(injector.getInstance(AuthenticationResource.class));
        environment.jersey().register(injector.getInstance(UserResource.class));
        environment.jersey().register(injector.getInstance(TokenResource.class));
    }

    private void registerExceptionMappers(Environment environment) {
        environment.jersey().register(injector.getInstance(BadRequestExceptionMapper.class));
        environment.jersey().register(injector.getInstance(TokenCreationExceptionMapper.class));
        environment.jersey().register(injector.getInstance(UnsupportedFilterExceptionMapper.class));
        environment.jersey().register(injector.getInstance(NoSuchUserExceptionMapper.class));
    }

    private void registerHealthChecks(Environment environment, EmailSender emailSender, Config config){
        environment.healthChecks().register(LogLevelInspector.class.getName(), new LogLevelInspector());
        environment.healthChecks().register(EmailHealthCheck.class.getName(),
                new EmailHealthCheck(emailSender, config.getEmailConfig()));
    }

    private static void configureAuthentication(JwtConsumer jwtConsumer, Environment environment) {
        JwtAuthFilter<ServiceUser> jwtAuthFilter = new JwtAuthFilter.Builder<ServiceUser>()
                .setJwtConsumer(jwtConsumer)
                .setRealm("realm")
                .setPrefix("Bearer")
                .setAuthenticator(new UserAuthenticator())
                .buildAuthFilter();
        environment.jersey().register(new AuthDynamicFeature(jwtAuthFilter));
        environment.jersey().register(new Binder(ServiceUser.class));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
    }

    private static void configureCors(Environment environment) {
        Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true");
    }

    private static void migrate(Config config, Environment environment) {
        ManagedDataSource dataSource = config.getDataSourceFactory().build(
                environment.metrics(), "flywayDataSource");
        Flyway flyway = config.getFlywayFactory().build(dataSource);
        // If migration fails an un-checked exception will be throw which will stop the app
        flyway.migrate();
    }

    private void schedulePasswordChecks(Config config, PasswordIntegrityCheckTask passwordIntegrityCheckTask) {
        Timer time = new Timer();
        time.schedule(
                passwordIntegrityCheckTask,
                0,
                config.getPasswordIntegrityChecksConfig().getDurationBetweenChecks().toMillis());
    }

}
