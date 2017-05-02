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

import java.io.Serializable;

import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

public class DispatchEntityEventTask extends ServerTask<VoidResult>implements Serializable {
    private static final long serialVersionUID = -1305243739417365803L;

    private final EntityEvent entityEvent;

    public DispatchEntityEventTask(final EntityEvent entityEvent) {
        this.entityEvent = entityEvent;
    }

    public EntityEvent getEntityEvent() {
        return entityEvent;
    }
}
