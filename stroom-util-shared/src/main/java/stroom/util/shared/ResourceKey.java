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

package stroom.util.shared;

import java.util.Map;

/**
 * Class that represents a key to a resource. This key has a name and a string
 * key.
 */
public class ResourceKey implements SharedObject {
    private static final long serialVersionUID = 8764851638854986846L;

    private String name;
    private String key;

    public static final String NAME = "name";
    public static final String KEY = "key";

    public ResourceKey(final String name, final String key) {
        this.name = name;
        this.key = key;
    }

    public ResourceKey(final Map<String, String> map) {
        this.name = map.get(NAME);
        this.key = map.get(KEY);
    }

    public ResourceKey() {
        // Default constructor necessary for GWT serialisation.
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (!(other instanceof ResourceKey)) {
            return false;
        }
        return this.key.equals(((ResourceKey) other).key);
    }

    @Override
    public String toString() {
        return key;
    }

    public void write(Map<String, String> map) {
        map.put(NAME, getName());
        map.put(KEY, getKey());
    }
}
