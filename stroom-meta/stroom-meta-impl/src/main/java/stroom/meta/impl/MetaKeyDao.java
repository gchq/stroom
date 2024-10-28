/*
 * Copyright 2017-2024 Crown Copyright
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

package stroom.meta.impl;

import stroom.util.shared.string.CIKey;

import java.util.Optional;

public interface MetaKeyDao {

    Optional<String> getNameForId(final int keyId);

    Optional<Integer> getIdForName(final CIKey name);

    default Optional<Integer> getIdForName(final String name) {
        return getIdForName(CIKey.of(name));
    }

    Integer getMinId();

    Integer getMaxId();
}
