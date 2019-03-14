/*
 * Copyright 2017 Crown Copyright
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

package stroom.core.migration._V07_00_00.entity.shared;

import stroom.core.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.migration._V07_00_00.util.shared._V07_00_00_HasId;
import stroom.core.migration._V07_00_00.util.shared._V07_00_00_HasType;
import stroom.core.migration._V07_00_00.util.shared._V07_00_00_HasUuid;

public final class _V07_00_00_DocRefUtil {
    public static final _V07_00_00_DocRef NULL_SELECTION = new _V07_00_00_DocRef.Builder().uuid("").name("None").type("").build();

    private _V07_00_00_DocRefUtil() {
        // Utility class.
    }

    public static _V07_00_00_DocRef create(final _V07_00_00_Entity entity) {
        if (entity == null) {
            return null;
        }

        String type = null;
        Long id = null;
        String uuid = null;
        String name = null;

        if (entity instanceof _V07_00_00_HasType) {
            type = entity.getType();
        }

        if (entity instanceof _V07_00_00_HasId) {
            id = ((_V07_00_00_HasId) entity).getId();

            // All equality is done on uuid so ensure uuid is set to id even if the entity doesn't have a uuid field.
            uuid = String.valueOf(id);
        }

        if (entity instanceof _V07_00_00_HasUuid) {
            uuid = ((_V07_00_00_HasUuid) entity).getUuid();
        }

        if (entity instanceof _V07_00_00_HasName) {
            name = ((_V07_00_00_HasName) entity).getName();
        }

        return new _V07_00_00_DocRef(type, uuid, name);
    }
}