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
import stroom.security.api.HashFunctionFactory;
import stroom.security.shared.HashAlgorithm;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;

import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public class HashFunctionFactoryImpl implements HashFunctionFactory {

    private final Map<HashAlgorithm, HashFunction> hashFunctionMap;

    public HashFunctionFactoryImpl() {
        final SecureRandom secureRandom = new SecureRandom();
        final List<HashFunction> hashFunctions = List.of(
                new ShaThree256Hasher(secureRandom),
                new ShaTwo256Hasher(secureRandom),
                new BCryptHasherLegacy(),
                new BCryptHasher(),
                new Argon2Hasher(secureRandom),
                new ShaTwo512Hasher(secureRandom));

        hashFunctionMap = CollectionUtil.enumMapBy(
                HashAlgorithm.class,
                HashFunction::getType,
                DuplicateMode.THROW,
                hashFunctions);
    }

    @Override
    public HashFunction getHashFunction(final HashAlgorithm hashAlgorithm) {
        final HashFunction hashFunction = hashFunctionMap.get(Objects.requireNonNull(hashAlgorithm));
        if (hashFunction == null) {
            throw new IllegalArgumentException("No HashFunction exists for algorithm " + hashAlgorithm);
        }
        return hashFunction;
    }
}
