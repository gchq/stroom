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

package stroom.util.entityevent;


import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

// TODO should be a record, but checkstyle throws an exception because of it for
public final class EntityEventKey {

    private final @Nullable EntityAction action;
    private final @NonNull String type;


    public EntityEventKey(@Nullable final EntityAction action,
                          @NonNull final String type) {
        Objects.requireNonNull(type);
        this.action = action;
        this.type = type;
    }

    public EntityEventKey(final EntityEvent entityEvent) {
        this(Objects.requireNonNull(entityEvent).getAction(), entityEvent.getType());
    }

    public static EntityEventKey wileCardedType(final EntityAction action) {
        return new EntityEventKey(action, EntityEvent.TYPE_WILDCARD);
    }

    public @Nullable EntityAction action() {
        return action;
    }

    public @NonNull String type() {
        return type;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final EntityEventKey that = (EntityEventKey) obj;
        return Objects.equals(this.action, that.action) &&
               Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, type);
    }

    @Override
    public String toString() {
        return "EntityEventKey[" +
               "action=" + action + ", " +
               "type=" + type + ']';
    }
}
