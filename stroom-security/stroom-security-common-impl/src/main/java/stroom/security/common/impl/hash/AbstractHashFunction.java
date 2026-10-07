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
import stroom.util.string.StringUtil;

import java.security.SecureRandom;
import java.util.Objects;

abstract class AbstractHashFunction implements HashFunction {

    private final SecureRandom secureRandom;

    AbstractHashFunction(final SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    public AbstractHashFunction() {
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String generateSalt() {
        return StringUtil.createRandomCode(secureRandom, 64);
    }

    protected String getSaltedValue(final String value, final String salt) {
        return salt != null
                ? salt + value
                : value;
    }
}
