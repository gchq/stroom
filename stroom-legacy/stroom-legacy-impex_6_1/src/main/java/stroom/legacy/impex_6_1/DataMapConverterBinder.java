/*
 * Copyright 2018 Crown Copyright
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

package stroom.legacy.impex_6_1;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;

public final class DataMapConverterBinder {
    private final MapBinder<ConverterType, DataMapConverter> mapBinder;

    private DataMapConverterBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ConverterType.class, DataMapConverter.class);
    }

    public static DataMapConverterBinder create(final Binder binder) {
        return new DataMapConverterBinder(binder);
    }

    public <T extends DataMapConverter> DataMapConverterBinder bind(final String type, final Class<T> clazz) {
        mapBinder.addBinding(new ConverterType(type)).to(clazz);
        return this;
    }

    public static class ConverterType {
        private final String type;

        <T extends DataMapConverter> ConverterType(final String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final ConverterType that = (ConverterType) o;
            return Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type);
        }
    }
}