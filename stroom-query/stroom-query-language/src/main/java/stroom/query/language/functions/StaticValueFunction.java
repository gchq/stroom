/*
 * Copyright 2017-2024 Crown Copyright
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

@ArchitecturalFunction
public class StaticValueFunction implements Function, Appendable {

    private final Val value;
    private final StaticValueGen gen;

    public StaticValueFunction(final Val value) {
        this.value = value;
        this.gen = new StaticValueGen(value);
    }

    public static StaticValueFunction of(final Val value) {
        return new StaticValueFunction(value);
    }

    public static StaticValueFunction of(final String value) {
        return new StaticValueFunction(ValString.create(value));
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
        value.appendString(sb);
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

    public Val getValue() {
        return gen.getValue();
    }
}
