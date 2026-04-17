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

package stroom.annotation.shared;


import stroom.docref.DocRef;

import java.util.Objects;

/**
 * Simply an Annotation's {@link DocRef} and its database ID value.
 */
public class AnnotationIdentity {

    private final String uuid;
    private final long id;

    public AnnotationIdentity(final String uuid,
                              final long id) {
        this.uuid = Objects.requireNonNull(uuid);
        this.id = id;
    }

    public DocRef getDocRef() {
        return new DocRef(Annotation.TYPE, uuid);
    }

    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnnotationIdentity that = (AnnotationIdentity) o;
        return id == that.id && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, id);
    }

    @Override
    public String toString() {
        return "AnnotationIdentity{" +
               "uuid=" + uuid +
               ", id=" + id +
               '}';
    }
}
