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

package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.shared.UserDesc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestProxySecurityContextImpl {

    private static final AppPermissionSet PROXY_PERM_SET = AppPermission.STROOM_PROXY.asAppPermissionSet();
    private static final String API_KEY = "sak_dummy_key";
    private static final UserDesc USER_DESC = UserDesc.forSubjectId("user1");

    @Mock
    private ProxyApiKeyService mockProxyApiKeyService;

    @Test
    void asProcessingUserResult_runsAsTokenlessProcessingUser() {
        // gh-5946 - Running as the processing user must not need a token from the IDP.
        final ProxySecurityContextImpl securityContext = createSecurityContext();

        final UserIdentity userIdentity = securityContext.asProcessingUserResult(
                securityContext::getUserIdentity);

        assertThat(userIdentity)
                .isSameAs(ProxyProcessingUserIdentity.INSTANCE);
        verifyNoInteractions(mockProxyApiKeyService);
    }

    @Test
    void asProcessingUser_isProcessingUser() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();
        final boolean[] isProcessingUser = new boolean[1];

        securityContext.asProcessingUser(() ->
                isProcessingUser[0] = securityContext.isProcessingUser());

        assertThat(isProcessingUser[0])
                .isTrue();
    }

    @Test
    void asProcessingUserResult_popsUserAfterwards() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();

        securityContext.asProcessingUserResult(() -> "foo");

        assertThat(securityContext.getUserIdentity())
                .isNull();
    }

    @Test
    void secureResult_processingUserPassesPermissionCheck() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();

        final String result = securityContext.asProcessingUserResult(() ->
                securityContext.secureResult(PROXY_PERM_SET, () -> "foo"));

        assertThat(result)
                .isEqualTo("foo");
        verifyNoInteractions(mockProxyApiKeyService);
    }

    @Test
    void secureResult_noUser() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();

        assertThatThrownBy(() -> securityContext.secureResult(PROXY_PERM_SET, () -> "foo"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void isProcessingUser_otherUser() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();
        final ApiKeyUserIdentity apiKeyUserIdentity = new ApiKeyUserIdentity(API_KEY, USER_DESC);

        final boolean isProcessingUser = securityContext.asUserResult(
                apiKeyUserIdentity,
                securityContext::isProcessingUser);

        assertThat(isProcessingUser)
                .isFalse();
    }

    @Test
    void isProcessingUser_noUser() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();

        assertThatThrownBy(securityContext::isProcessingUser)
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void hasAppPermissions_apiKeyUserWithPerms() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();
        final ApiKeyUserIdentity apiKeyUserIdentity = new ApiKeyUserIdentity(API_KEY, USER_DESC);
        when(mockProxyApiKeyService.verifyApiKey(any()))
                .thenReturn(Optional.of(USER_DESC));

        final boolean hasPerms = securityContext.hasAppPermissions(apiKeyUserIdentity, PROXY_PERM_SET);

        assertThat(hasPerms)
                .isTrue();
        final ArgumentCaptor<VerifyApiKeyRequest> requestCaptor = ArgumentCaptor.forClass(VerifyApiKeyRequest.class);
        verify(mockProxyApiKeyService).verifyApiKey(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .isEqualTo(new VerifyApiKeyRequest(API_KEY, PROXY_PERM_SET));
    }

    @Test
    void hasAppPermissions_apiKeyUserWithoutPerms() {
        final ProxySecurityContextImpl securityContext = createSecurityContext();
        final ApiKeyUserIdentity apiKeyUserIdentity = new ApiKeyUserIdentity(API_KEY, USER_DESC);
        when(mockProxyApiKeyService.verifyApiKey(any()))
                .thenReturn(Optional.empty());

        final boolean hasPerms = securityContext.hasAppPermissions(apiKeyUserIdentity, PROXY_PERM_SET);

        assertThat(hasPerms)
                .isFalse();
    }

    private ProxySecurityContextImpl createSecurityContext() {
        return new ProxySecurityContextImpl(mockProxyApiKeyService);
    }
}
