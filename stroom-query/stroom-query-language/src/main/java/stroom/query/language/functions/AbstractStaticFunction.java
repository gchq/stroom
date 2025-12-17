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

import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.Map;

abstract class AbstractStaticFunction implements Function, Appendable {

    private final String name;
    private final Generator gen;

    AbstractStaticFunction(final String name, final Generator gen) {
        this.name = name;
        this.gen = gen;
    }

    @Override
    public void setParams(final Param[] params) {
        // Ignore
    }

    @Override
    public void setStaticMappedValues(final Map<String, String> staticMappedValues) {
        // Ignore
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        // Ignore
    }

    @Override
    public Generator createGenerator() {
        return gen;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendString(sb);
        return sb.toString();
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append(name);
        sb.append("()");
    }

    @Override
    public boolean isAggregate() {
        return false;
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    @Override
    public boolean requiresChildData() {
        return false;
    }
}
