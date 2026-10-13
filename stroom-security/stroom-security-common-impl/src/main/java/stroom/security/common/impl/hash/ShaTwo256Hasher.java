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


import stroom.security.shared.HashAlgorithm;
import stroom.util.string.Base58;

import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;

class ShaTwo256Hasher extends AbstractHashFunction {

    public ShaTwo256Hasher() {
    }

    public ShaTwo256Hasher(final SecureRandom secureRandom) {
        super(secureRandom);
    }

    @Override
    public String hash(final String value, final String salt) {
        final String saltedVal = getSaltedValue(value, salt);
        return Base58.encode(DigestUtils.sha256(saltedVal));
    }

    @Override
    public HashAlgorithm getType() {
        return HashAlgorithm.SHA2_256;
    }
}
