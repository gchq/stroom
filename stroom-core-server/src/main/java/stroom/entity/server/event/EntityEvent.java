/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.server.event;

import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityAction;

import java.io.Serializable;

public class EntityEvent implements Serializable {
    private static final long serialVersionUID = -6646086368064417052L;

    private DocRef docRef;
    private EntityAction action;

    public EntityEvent() {
    }

    public EntityEvent(final DocRef docRef, final EntityAction action) {
        this.docRef = docRef;
        this.action = action;
    }

    public static void fire(final EntityEventBus eventBus, final DocRef docRef,
                            final EntityAction action) {
        eventBus.fire(new EntityEvent(docRef, action));
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public EntityAction getAction() {
        return action;
    }

    public interface Handler {
        void onChange(EntityEvent event);
    }
}
