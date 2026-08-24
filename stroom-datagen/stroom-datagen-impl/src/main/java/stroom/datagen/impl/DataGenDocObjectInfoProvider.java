/*
 * Copyright 2019 Crown Copyright
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

package stroom.datagen.impl;

import stroom.datagen.shared.DataGenDoc;
import stroom.event.logging.api.ObjectInfoProvider;

import event.logging.BaseObject;
import event.logging.OtherObject;

/**
 * Describes a {@link DataGenDoc} to the event logging service, so that actions on a data generator
 * appear in the audit log with its name, UUID and description rather than as an opaque object.
 */
class DataGenDocObjectInfoProvider implements ObjectInfoProvider {

    @Override
    public BaseObject createBaseObject(final Object obj) {
        final DataGenDoc dataGenDoc = (DataGenDoc) obj;
        final OtherObject.Builder<Void> builder = OtherObject.builder()
                .withType(dataGenDoc.getType())
                .withId(dataGenDoc.getUuid())
                .withName(dataGenDoc.getName())
                .withDescription(dataGenDoc.getDescription());

        return builder.build();
    }

    @Override
    public String getObjectType(final Object object) {
        return object.getClass().getSimpleName();
    }
}
