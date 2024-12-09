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
 *
 */

package stroom.pipeline;

import stroom.docstore.shared.AbstractDoc;
import stroom.event.logging.api.ObjectInfoProvider;

import event.logging.BaseObject;
import event.logging.OtherObject;

class DocObjectInfoProvider implements ObjectInfoProvider {
    @Override
    public BaseObject createBaseObject(final Object obj) {
        final AbstractDoc doc = (AbstractDoc) obj;

        return OtherObject.builder()
                .withId(doc.getUuid())
                .withType(doc.getType())
                .withName(doc.getName())
                .build();
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
