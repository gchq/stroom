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

package stroom.security.openid.api;

import org.jose4j.jwk.PublicJsonWebKey;

import java.util.List;

/**
 * The signing keys of stroom's internal identity provider.
 * <p>
 * The two methods are deliberately not interchangeable. Exactly one key is signed with, but
 * several are published, because a token must keep verifying for its whole lifetime and so the
 * key that signed it has to stay published after it has stopped being signed with. Taking the
 * signing key from {@link #list()} would therefore be a bug, see {@link #getActiveKey()}.
 * </p>
 */
public interface PublicJsonWebKeyProvider {

    /**
     * Every key a token might legitimately have been signed by: the active key and any that have
     * been retired but could still have unexpired tokens outstanding.
     * <p>
     * This is what the JWKS endpoint publishes and what verification resolves against, by
     * {@code kid}. Never use it to choose a key to sign with.
     * </p>
     *
     * @return The publishable keys, newest first. Never empty; a key is created if there is none.
     */
    List<PublicJsonWebKey> list();

    /**
     * The one key that new tokens must be signed with.
     * <p>
     * Created on demand if there is no active key, so this never returns null. Note this is not
     * the same as {@code list().get(0)}: that would happily hand back a retired key, producing
     * tokens that verify now and stop verifying when the key is finally deleted.
     * </p>
     *
     * @return The active key.
     */
    PublicJsonWebKey getActiveKey();
}
