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

package stroom.floormap.impl;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.floormap.shared.FloorMapDoc;

import event.logging.BaseObject;
import event.logging.OtherObject;

/// Provides object information for event logging of [FloorMapDoc] documents.
///
/// Converts [FloorMapDoc] instances into [OtherObject] representations
/// suitable for the event logging framework.
class FloorMapDocObjectInfoProvider implements ObjectInfoProvider {

    /// Creates an event-logging [BaseObject] from a [FloorMapDoc] instance.
    ///
    /// @param obj the object to convert, expected to be a [FloorMapDoc]
    /// @return an [OtherObject] populated with the document's type, UUID, name, and description
    @Override
    public BaseObject createBaseObject(final Object obj) {
        final FloorMapDoc floorMapDoc = (FloorMapDoc) obj;
        return OtherObject.builder()
                .withType(floorMapDoc.getType())
                .withId(floorMapDoc.getUuid())
                .withName(floorMapDoc.getName())
                .withDescription(floorMapDoc.getDescription())
                .build();
    }

    /// Returns the simple class name of the given object as the object type identifier.
    ///
    /// @param object the object whose type to determine
    /// @return the simple class name of the object
    @Override
    public String getObjectType(final Object object) {
        return object.getClass().getSimpleName();
    }
}
