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

import java.text.ParseException;
import java.util.Map;

abstract class AbstractCurrentUser extends AbstractFunction {

    private Generator gen = Null.GEN;

    public AbstractCurrentUser(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
    }

    @Override
    public void setStaticMappedValues(final Map<String, String> staticMappedValues) {
        final String v = staticMappedValues.get(getKey());
        if (v != null) {
            gen = new StaticValueGen(ValString.create(v));
        }
    }

    @Override
    public Generator createGenerator() {
        return gen;
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    abstract String getKey();
}
