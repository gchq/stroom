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

package stroom.entity.shared;

import stroom.query.api.v2.DocRef;
import stroom.util.shared.HasId;
import stroom.util.shared.HasType;

public final class DocRefUtil {
    private DocRefUtil() {
        // Utility class.
    }

    public static DocRef create(final Entity entity) {
        if (entity == null) {
            return null;
        }

        String type = null;
        Long id = null;
        String uuid = null;
        String name = null;

        if (entity instanceof HasType) {
            type = entity.getType();
        }

        if (entity instanceof HasId) {
            id = ((HasId) entity).getId();

            // All equality is done on uuid so ensure uuid is set to id even if the entity doesn't have a uuid field.
            uuid = String.valueOf(id);
        }

        try {
            if (entity instanceof HasUuid) {
                uuid = ((HasUuid) entity).getUuid();
            }
        } catch (final RuntimeException e) {
            // Ignore, we might get an exception getting some fields on lazy hibernate objects.
        }

        try {
            if (entity instanceof HasName) {
                name = ((HasName) entity).getName();
            }
        } catch (final RuntimeException e) {
            // Ignore, we might get an exception getting some fields on lazy hibernate objects.
        }

        DocRef docRef = new DocRef(type, uuid, name);
        docRef.setId(id);
        return docRef;
    }
}