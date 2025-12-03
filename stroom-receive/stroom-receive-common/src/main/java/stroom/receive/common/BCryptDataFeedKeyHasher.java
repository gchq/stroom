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

package stroom.receive.common;

import stroom.util.shared.NullSafe;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.Objects;

public class BCryptDataFeedKeyHasher implements DataFeedKeyHasher {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public HashOutput hash(final String dataFeedKey) {
        final String generatedSalt = BCrypt.gensalt(10, secureRandom);
        final String hash = BCrypt.hashpw(Objects.requireNonNull(dataFeedKey), generatedSalt);
        return new HashOutput(hash, generatedSalt);
    }

    @Override
    public boolean verify(final String dataFeedKey, final String hash, final String salt) {
        if (NullSafe.isEmptyString(dataFeedKey)) {
            return false;
        } else {
            return BCrypt.checkpw(dataFeedKey, hash);
        }
    }

    @Override
    public DataFeedKeyHashAlgorithm getAlgorithm() {
        return DataFeedKeyHashAlgorithm.BCRYPT_2A;
    }
}
