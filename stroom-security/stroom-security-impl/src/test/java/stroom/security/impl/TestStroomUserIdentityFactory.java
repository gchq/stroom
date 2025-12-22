/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.UserService;
import stroom.security.mock.MockSecurityContext;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.shared.User;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LogUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestStroomUserIdentityFactory {

    public static final String USER_123_SUBJECT = "user123";
    @Mock
    private UserService mockUserService;
    @Mock
    private OpenIdConfiguration mockOpenIdConfiguration;
    @Mock
    private EntityEventBus mockEntityEventBus;
    @Mock
    private JwtClaims mockJwtClaims;
    @Mock
    private JwtContext mockJwtContext;
    @Mock
    private HttpServletRequest mockHttpServletRequest;

    @Test
    void mapApiIdentity() throws MalformedClaimException {
        final String displayNameClaim = "disp";
        final User user = User.builder()
                .subjectId(USER_123_SUBJECT)
                .displayName(USER_123_SUBJECT)
                .fullName(null)
                .build();

        Mockito.when(mockOpenIdConfiguration.getIdentityProviderType())
                .thenReturn(IdpType.EXTERNAL_IDP);
        Mockito.when(mockOpenIdConfiguration.getUniqueIdentityClaim())
                .thenReturn(OpenId.CLAIM__SUBJECT);
        Mockito.when(mockOpenIdConfiguration.getUserDisplayNameClaim())
                .thenReturn(displayNameClaim);
        Mockito.when(mockJwtClaims.getClaimValue(Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    final String claim = invocation.getArgument(0, String.class);
                    return switch (claim) {
                        case OpenId.CLAIM__SUBJECT -> USER_123_SUBJECT;
                        case OpenId.CLAIM__NAME -> null;
                        case displayNameClaim -> null;
                        default -> throw new IllegalArgumentException(LogUtil.message("'{}' not expected", claim));
                    };
                });
        Mockito.when(mockJwtContext.getJwtClaims())
                .thenReturn(mockJwtClaims);
        Mockito.when(mockUserService.getOrCreateUser(Mockito.eq(USER_123_SUBJECT)))
                .thenReturn(user);

        final StroomUserIdentityFactory stroomUserIdentityFactory = new StroomUserIdentityFactory(
                null,
                () -> mockOpenIdConfiguration,
                null,
                null,
                null,
                null,
                () -> mockUserService,
                new MockSecurityContext(),
                null,
                mockEntityEventBus,
                null,
                null,
                AuthorisationConfig::new,
                new CacheManagerImpl(),
                new SimplePathCreator(() -> null, () -> null));

        stroomUserIdentityFactory.getApiUserIdentity(mockJwtContext, mockHttpServletRequest);

        Mockito.verify(mockUserService, Mockito.never())
                .update(Mockito.any());
    }
}
