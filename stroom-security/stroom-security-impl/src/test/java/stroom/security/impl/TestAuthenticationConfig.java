/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.impl;

import stroom.security.openid.api.IdpType;
import stroom.test.common.AbstractValidatorTest;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAuthenticationConfig extends AbstractValidatorTest {

    @Test
    void defaultConfigIsValid() {
        assertThat(validate(new AuthenticationConfig())).isEmpty();
    }

    @Test
    void edgeAuthenticationRequiresAnExternalIdp() {
        // Edge mode with stroom's internal IDP is incoherent: an edge that authenticates against
        // a different IDP cannot front the IDP itself. Fail fast at startup.
        final AuthenticationConfig config = new AuthenticationConfig(
                null,
                null,
                new CsrfConfig(true),
                new EdgeAuthenticationConfig(true, null),
                null,
                new StroomOpenIdConfig().withIdentityProviderType(IdpType.INTERNAL_IDP),
                null);

        assertThat(validate(config))
                .anyMatch(violation -> violation.getMessage().contains("EXTERNAL_IDP"));
    }

    @Test
    void edgeAuthenticationWithExternalIdpIsValid() {
        final AuthenticationConfig config = new AuthenticationConfig(
                null,
                null,
                new CsrfConfig(true),
                new EdgeAuthenticationConfig(true, null),
                null,
                new StroomOpenIdConfig().withIdentityProviderType(IdpType.EXTERNAL_IDP),
                null);

        assertThat(config.isEdgeAuthenticationValid()).isTrue();
    }

    @Test
    void edgeConfigSerialisationRoundTrips() {
        final EdgeAuthenticationConfig config = new EdgeAuthenticationConfig(
                true,
                new EdgeLogoutConfig(
                        List.of("AWSELBAuthSessionCookie"),
                        "https://my-domain.auth.eu-west-2.amazoncognito.com/logout"));

        final String json = JsonUtil.writeValueAsString(config);
        final EdgeAuthenticationConfig deserialised =
                JsonUtil.readValue(json, EdgeAuthenticationConfig.class);

        assertThat(deserialised).isEqualTo(config);
    }

    @Test
    void csrfConfigSerialisationRoundTrips() {
        final CsrfConfig config = new CsrfConfig(false);

        final String json = JsonUtil.writeValueAsString(config);
        final CsrfConfig deserialised = JsonUtil.readValue(json, CsrfConfig.class);

        assertThat(deserialised).isEqualTo(config);
    }

    @Test
    void absentPropertiesGetDefaults() {
        // A yaml with just 'enabled: true' must default the rest.
        final EdgeAuthenticationConfig config = JsonUtil.readValue(
                "{\"enabled\": true}", EdgeAuthenticationConfig.class);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getLogout()).isNotNull();
        assertThat(config.getLogout().getCookiesToExpire()).isEmpty();
        assertThat(config.getLogout().getSignOutUrl()).isNull();
    }
}
