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

package stroom.widget.menu.client.presenter;

import java.util.Objects;

public abstract class Item {

    private static int lastId = 0;

    private final int id;
    private final int priority;

    protected Item(final int priority) {
        this.id = lastId++;
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Item)) {
            return false;
        }
        final Item item = (Item) o;
        return id == item.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }


    // --------------------------------------------------------------------------------


    protected abstract static class AbstractBuilder<T extends Item, B extends Item.AbstractBuilder<T, ?>> {

        protected int priority;

        public B priority(final int priority) {
            this.priority = priority;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
