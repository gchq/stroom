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

package stroom.pipeline.shared.data;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAddRemoveListBuilder<E, T extends AbstractAddRemove<E>, B
        extends AbstractAddRemoveListBuilder<E, T, ?>>
        extends AbstractBuilder<T, B> {

    private final List<E> addList = new ArrayList<>();
    private final List<E> removeList = new ArrayList<>();

    public AbstractAddRemoveListBuilder() {

    }

    public AbstractAddRemoveListBuilder(final T init) {
        if (init != null) {
            addList.addAll(NullSafe.list(init.add));
            removeList.addAll(NullSafe.list(init.remove));
        }
    }

    public List<E> getAddList() {
        return addList;
    }

    List<E> copyAddList() {
        return NullSafe.isEmptyCollection(addList)
                ? null
                : new ArrayList<>(addList);
    }

    public List<E> getRemoveList() {
        return removeList;
    }

    List<E> copyRemoveList() {
        return NullSafe.isEmptyCollection(removeList)
                ? null
                : new ArrayList<>(removeList);
    }
}
