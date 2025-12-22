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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MockIntCrud<T> implements HasIntCrud<T>, Clearable {

    private final Map<Integer, T> map = new HashMap<>();
    private final AtomicInteger generatedId = new AtomicInteger();

    @Override
    public T create(final T t) {
        final int id = generatedId.incrementAndGet();
        setId(t, id);
        map.put(id, t);
        return t;
    }

    @Override
    public Optional<T> fetch(final int id) {
        return Optional.ofNullable(map.get(id));
    }

    @Override
    public T update(final T t) {
        final Integer id = getId(t);
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

    private Integer getId(final T t) {
        try {
            final Method method = t.getClass().getMethod("getId");
            return (Integer) method.invoke(t);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private void setId(final T t, final Integer id) {
        try {
            final Method method = t.getClass().getMethod("setId", Integer.class);
            method.invoke(t, id);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            try {
                final Method method = t.getClass().getMethod("setId", Integer.TYPE);
                method.invoke(t, id);
            } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e2) {
                throw new RuntimeException(e2);
            }
        }
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
