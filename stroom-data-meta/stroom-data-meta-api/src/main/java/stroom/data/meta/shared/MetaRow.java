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

package stroom.data.meta.shared;

import stroom.docref.SharedObject;

import java.util.HashMap;
import java.util.Map;

public class MetaRow implements SharedObject {
    private static final long serialVersionUID = -8198186456924478908L;

    private Meta data;
    private Map<String, String> attributes = new HashMap<>();

    public MetaRow() {
        // Default constructor necessary for GWT serialisation.
    }

    public MetaRow(Meta data) {
        setData(data);
    }

    public Meta getData() {
        return data;
    }

    public void setData(Meta data) {
        this.data = data;
    }

    public void addAttribute(final String name, final String value) {
        attributes.put(name, value);
    }

    public String getAttributeValue(final String name) {
        return attributes.get(name);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof MetaRow)) return false;

        final MetaRow that = (MetaRow) o;

        return data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
