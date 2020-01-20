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

package stroom.auth.service.resources.support;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import stroom.auth.service.App;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

public abstract class Dropwizard_IT extends Database_IT {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(
            WireMockConfiguration.options().port(8080));

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    protected static String BASE_TASKS_URL;
    protected static String HEALTH_CHECKS_URL;

    protected static int appPort;
    protected static int adminPort;

    protected static UserManager userManager = new UserManager();
    protected static AuthenticationManager authenticationManager = new AuthenticationManager();
    protected static TokenManager tokenManager = new TokenManager();

    private static DropwizardTestSupport dropwizardTestSupport;

    @BeforeClass
    public static void setupClass() throws InterruptedException {

        dropwizardTestSupport = new DropwizardTestSupport(
                App.class,
                "config.generated.yml",
                // We're not using the DropwizardClassRule because we need to inject the JDBC URL, and we need that from
                // the MySQLContainer class rule in Database_IT. It's not available at that point.
                ConfigOverride.config("database.url", mysql.getJdbcUrl()),
                // We need prevent forced password changing, because that breaks the automatic login.
                // TODO: upgrade the test login automation to handle forced password changes.
                ConfigOverride.config("passwordIntegrityChecks.forcePasswordChangeOnFirstLogin", "false"));
        dropwizardTestSupport.before();


        appPort = dropwizardTestSupport.getLocalPort();
        authenticationManager.setPort(appPort);
        userManager.setPort(appPort);
        tokenManager.setPort(appPort);

        adminPort = dropwizardTestSupport.getAdminPort();
        BASE_TASKS_URL = "http://localhost:" + adminPort + "/tasks/";
        HEALTH_CHECKS_URL = "http://localhost:" + adminPort + "/healthcheck?pretty=true";
        Thread.sleep(2000);
    }

    @AfterClass
    public static void afterClass() {
        dropwizardTestSupport.after();
    }

    @Before
    public void before(){
        stubFor(get(urlEqualTo("/api/appPermissions/v1/byName/admin"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[\"Manager Users\", \"Administrator\"]")
                        .withStatus(200)));
        stubFor(post(urlEqualTo("/api/authorisation/v1/isAuthorised/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)));
        stubFor(get(urlPathMatching("/api/appPermissions/v1/byName/userEmail.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[\"Some Permission\"]")
                        .withStatus(200)));
    }
}
