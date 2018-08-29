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

package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.docref.SharedObject;

public class SharedDocRef extends DocRef implements SharedObject {
    public SharedDocRef() {
    }

    public SharedDocRef(final String type, String uuid) {
        super(type, uuid);
    }

    public SharedDocRef(final String type, String uuid, final String name) {
        super(type, uuid, name);
    }

    public static SharedDocRef create(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

        return new SharedDocRef(docRef.getType(), docRef.getUuid(), docRef.getName());
    }

    protected static abstract class BaseBuilder<T extends SharedDocRef, CHILD_CLASS extends BaseBuilder<T, ?>> {
        private String type;

        private String uuid;

        private String name;

        protected BaseBuilder() {
        }

        public CHILD_CLASS type(final String value) {
            this.type = value;
            return self();
        }

        public CHILD_CLASS uuid(final String value) {
            this.uuid = value;
            return self();
        }

        public CHILD_CLASS name(final String value) {
            this.name = value;
            return self();
        }

        protected String getType() {
            return type;
        }

        protected String getUuid() {
            return uuid;
        }

        protected String getName() {
            return name;
        }

        protected abstract CHILD_CLASS self();

        public abstract T build();
    }

    public static class Builder extends BaseBuilder<SharedDocRef, Builder> {
        public Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SharedDocRef build() {
            return new SharedDocRef(getType(), getUuid(), getName());
        }
    }
}
