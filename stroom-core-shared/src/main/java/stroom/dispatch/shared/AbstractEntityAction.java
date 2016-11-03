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

package stroom.dispatch.shared;

import stroom.entity.shared.Entity;
import stroom.entity.shared.HasEntity;

public abstract class AbstractEntityAction<E extends Entity> extends Action<E>implements HasEntity<E> {
    private static final long serialVersionUID = 8804463889602947196L;

    private E entity;
    private String taskName;

    public AbstractEntityAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public AbstractEntityAction(final E entity, final String taskName) {
        this.entity = entity;
        this.taskName = taskName;
    }

    @Override
    public E getEntity() {
        return entity;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }
}
