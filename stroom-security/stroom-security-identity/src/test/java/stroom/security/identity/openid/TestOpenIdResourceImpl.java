/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.openid;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.identity.exceptions.BadRequestException;

import event.logging.AuthenticateOutcomeReason;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOpenIdResourceImpl {

    @Test
    void tokenErrorReasonIsNotLeakedToTheCaller() {
        // The token endpoint must not tell the caller which specific check failed, or it becomes an oracle
        // for enumerating valid client ids, redirect uris and secrets. The specific reason is still logged.
        final OpenIdService openIdService = mock(OpenIdService.class);
        when(openIdService.token(any())).thenThrow(new BadRequestException(
                "someClient", AuthenticateOutcomeReason.OTHER, "Incorrect secret"));

        final OpenIdResourceImpl resource = new OpenIdResourceImpl(
                () -> openIdService,
                null,
                null,
                null,
                () -> mock(StroomEventLoggingService.class));

        final MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        assertThatThrownBy(() -> resource.token(formParams))
                .isInstanceOf(WebApplicationException.class)
                .hasMessage("The token request is invalid.")
                .hasMessageNotContaining("secret");
    }
}
