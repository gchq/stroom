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

package stroom.query.language.functions;

public enum FunctionCategory {
    AGGREGATE("Aggregate"),
    CAST("Cast"),
    DATE("Date"),
    LINK("Link"),
    LOGIC("Logic"),
    LOOKUP("Lookup"),
    MATHEMATICS("Mathematics"),
    PARAM("Param"),
    SELECTION("Selection"),
    STRING("String"),
    TYPE_CHECKING("Type Checking"),
    URI("URI"),
    VALUE("Value");

    private final String name;

    FunctionCategory(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
