/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.annotation.impl;

import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;

import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(
        type = "Annotation",
        action = {EntityAction.UPDATE})
public class AnnotationState implements EntityEvent.Handler {

    private long lastChangeTime;

    public long getLastChangeTime() {
        return lastChangeTime;
    }

    @Override
    public void onChange(final EntityEvent event) {
        lastChangeTime = System.currentTimeMillis();
    }
}
