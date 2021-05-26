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

package stroom.explorer.shared;

public enum DocumentTypeGroup {

    STRUCTURE(1, "Structure"),
    DATA_PROCESSING(2, "Data Processing"),
    TRANSFORMATION(3, "Transformation"),
    SEARCH(4, "Search"),
    INDEXING(5, "Indexing"),
    CONFIGURATION(6, "Configuration"),
    SYSTEM(100, "System");

    private final int priority;
    private final String name;

    DocumentTypeGroup(final int priority, final String name) {
        this.priority = priority;
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public String getName() {
        return name;
    }
}
