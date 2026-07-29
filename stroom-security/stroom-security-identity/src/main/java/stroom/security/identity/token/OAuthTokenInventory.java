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

package stroom.security.identity.token;

import stroom.security.openid.api.TokenInventory;
import stroom.security.openid.api.TokenSummary;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Exposes the token inventory across the seam.
 * <p>
 * Thin by design: it exists to own the "what time is it" decision, so that the DAO stays clock-free and
 * therefore testable at expiry boundaries, and so that the RP side never has to think about expiry semantics at
 * all.
 * </p>
 *
 * @see TokenInventory
 */
@Singleton
public class OAuthTokenInventory implements TokenInventory {

    private final OAuthTokenDao oAuthTokenDao;

    @Inject
    OAuthTokenInventory(final OAuthTokenDao oAuthTokenDao) {
        this.oAuthTokenDao = oAuthTokenDao;
    }

    @Override
    public Map<String, TokenSummary> summariseUsableTokensBySubject() {
        return oAuthTokenDao.summariseUsableBySubject(System.currentTimeMillis());
    }
}
