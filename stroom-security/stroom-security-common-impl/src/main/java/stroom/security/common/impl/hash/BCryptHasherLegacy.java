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

package stroom.security.common.impl.hash;


import stroom.security.api.HashFunction;
import stroom.security.shared.HashAlgorithm;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

class BCryptHasherLegacy implements HashFunction {

    @Override
    public String generateSalt() {
        return BCrypt.gensalt();
    }

    @Override
    public String hash(final String value, final String salt) {
        return BCrypt.hashpw(
                Objects.requireNonNull(value),
                Objects.requireNonNullElseGet(salt, BCrypt::gensalt));
    }

    @Override
    public boolean verify(final String value, final String hash) {
        // Salt is baked into hash, so we don't need to pass it in
        return verify(value, hash, null);
    }

    @Override
    public boolean verify(final String value,
                          final String hash,
                          final String ignoredSalt) {
        if (value == null) {
            return false;
        } else {
            // Salt is encoded in the hash, so ignore the passed salt
            return BCrypt.checkpw(value, hash);
        }
    }

    @Override
    public HashAlgorithm getType() {
        return HashAlgorithm.BCRYPT_LEGACY;
    }
}
