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

package stroom.processor.impl;

import stroom.util.shared.Clearable;
import stroom.util.shared.HasIntCrud;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MockIntCrud<T> implements HasIntCrud<T>, Clearable {

    private final Map<Integer, T> map = new HashMap<>();
    private final AtomicInteger generatedId = new AtomicInteger();
    private final BiFunction<T, Integer, T> setIdFunction;
    private final Function<T, Integer> getIdFunction;

    public MockIntCrud(final BiFunction<T, Integer, T> setIdFunction,
                       final Function<T, Integer> getIdFunction) {
        this.setIdFunction = setIdFunction;
        this.getIdFunction = getIdFunction;
    }

    @Override
    public T create(final T t) {
        final int id = generatedId.incrementAndGet();
        map.put(id, setIdFunction.apply(t, id));
        return t;
    }

    @Override
    public Optional<T> fetch(final int id) {
        return Optional.ofNullable(map.get(id));
    }

    @Override
    public T update(final T t) {
        final Integer id = getIdFunction.apply(t);
        if (id == null) {
            throw new NullPointerException("Not present");
        }

        map.put(id, t);
        return t;
    }

    @Override
    public boolean delete(final int id) {
        return map.remove(id) != null;
    }

    public Map<Integer, T> getMap() {
        return map;
    }

    @Override
    public void clear() {
        map.clear();
        generatedId.set(0);
    }
}
