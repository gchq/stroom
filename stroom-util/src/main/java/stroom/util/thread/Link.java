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

package stroom.util.thread;

public class Link<T> {
    private final T object;
    private final Link<T> parent;

    public Link(final T object) {
        this.object = object;
        this.parent = null;
    }

    public Link(final T object, final Link<T> parent) {
        this.object = object;
        this.parent = parent;
    }

    public T getObject() {
        return object;
    }

    public Link<T> getParent() {
        return parent;
    }
}
