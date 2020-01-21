/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.auth.config;

import com.bendb.dropwizard.jooq.JooqFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.flyway.FlywayFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Singleton
public final class AuthenticationConfig extends AbstractConfig implements HasDbConfig {

    private DbConfig dbConfig = new DbConfig();

//    @Valid
//    @NotNull
//    @JsonProperty("database")
//    private DataSourceFactory dataSourceFactory = new DataSourceFactory();

//    @Valid
//    @NotNull
//    @JsonProperty("flyway")
//    private FlywayFactory flywayFactory = new FlywayFactory();

//    @Valid
//    @NotNull
//    @JsonProperty("jooq")
//    private JooqFactory jooqFactory = new JooqFactory();

    @Valid
    @NotNull
    @JsonProperty
    private String certificateDnPattern = ".*\\((.*)\\)";

    @Valid
    @NotNull
    @JsonProperty
    private int certificateDnCaptureGroupIndex = 1;

    //TODO: change all these URLs so they exclude the actual FQDN. Just have them be the paths.
    // TODO: 'Define' below means they need to be in config, but that's only because they include the domain. Change that first.
    @Valid
    @NotNull
    @JsonProperty
    private String loginUrl = "https://localhost/s/login"; // TODO define

    @Valid
    @NotNull
    @JsonProperty
    private String changePasswordUrl = "https://localhost/s/changepassword"; // TODO define

    @Valid
    @NotNull
    @JsonProperty
    private String stroomUrl = "https://localhost/"; // TODO define

    @Valid
    @NotNull
    @JsonProperty
    private String advertisedHost = "https://localhost";

    @Nullable
    @JsonProperty
    private Integer httpPort;

    @Nullable
    @JsonProperty
    private Integer httpsPort;

    @Nullable
    @JsonProperty
    private Integer failedLoginLockThreshold = 3;

    @Nullable
    @JsonProperty
    private String unauthorisedUrl = "https://localhost/s/unauthorised"; // TODO define

    @Nullable
    @JsonProperty("email")
    private EmailConfig emailConfig = new EmailConfig();

    @Nullable
    @JsonProperty("token")
    private TokenConfig tokenConfig = new TokenConfig();

    @Nullable
    @JsonProperty("sessionIdCookieMaxAge")
    private int sessionIdCookieMaxAge = 2592000; // 259200 = 1 month

    @NotNull
    @JsonProperty("userService")
    private UserServiceConfig userServiceConfig = new UserServiceConfig();

    @NotNull
    @JsonProperty("passwordIntegrityChecks")
    private PasswordIntegrityChecksConfig passwordIntegrityChecksConfig = new PasswordIntegrityChecksConfig();

    @NotNull
    @JsonProperty("ownPath")
    private String ownPath = "api/auth/authentication";

    @NotNull
    @JsonProperty("authorisationService")
    private AuthorisationServiceConfig authorisationServiceConfig = new AuthorisationServiceConfig();

    @Nullable
    @JsonProperty("stroom")
    private StroomConfig stroomConfig = new StroomConfig();

//    public final DataSourceFactory getDataSourceFactory() {
//        return this.dataSourceFactory;
//    }
//
//    public final FlywayFactory getFlywayFactory() {
//        return this.flywayFactory;
//    }
//
//    public final JooqFactory getJooqFactory() {
//        return this.jooqFactory;
//    }

    public final String getCertificateDnPattern() {
        return this.certificateDnPattern;
    }

    public final String getLoginUrl() {
        return this.loginUrl;
    }

    public String getChangePasswordUrl() {
        return changePasswordUrl;
    }

    public final String getStroomUrl() {
        return this.stroomUrl;
    }

    public final String getAdvertisedHost() {
        return this.advertisedHost;
    }

//    public final Integer getHttpPort() {
//        return getPort();
//    }
//
//    public final Integer getHttpsPort() {
//        return getPort();
//    }

    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    public Integer getFailedLoginLockThreshold() {
        return this.failedLoginLockThreshold;
    }

    public UserServiceConfig getUserServiceConfig() {
        return userServiceConfig;
    }

    public TokenConfig getTokenConfig() {
        return tokenConfig;
    }

    @Nullable
    public int getSessionIdCookieMaxAge() {
        return sessionIdCookieMaxAge;
    }

    @Nullable
    public String getUnauthorisedUrl() {
        return unauthorisedUrl;
    }

    public PasswordIntegrityChecksConfig getPasswordIntegrityChecksConfig() {
        return passwordIntegrityChecksConfig;
    }

    public int getCertificateDnCaptureGroupIndex() {
        return certificateDnCaptureGroupIndex;
    }

    public String getOwnPath() {
        return ownPath;
    }

    public AuthorisationServiceConfig getAuthorisationServiceConfig() {
        return authorisationServiceConfig;
    }

//    private Integer getPort() {
//        DefaultServerFactory serverFactory = (DefaultServerFactory) this.getServerFactory();
//        Integer port = serverFactory.getApplicationConnectors().stream()
//                .filter(connectorFactory -> connectorFactory instanceof HttpConnectorFactory)
//                .map(connectorFactory -> (HttpConnectorFactory) connectorFactory)
//                .map(HttpConnectorFactory::getPort)
//                .findFirst()
//                .get();
//        return port;
//    }

    public StroomConfig getStroomConfig() {
        return stroomConfig;
    }

    @Override
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
