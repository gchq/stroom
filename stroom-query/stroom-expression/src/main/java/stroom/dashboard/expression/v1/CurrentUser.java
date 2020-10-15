/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import java.text.ParseException;
import java.util.Map;

class CurrentUser extends AbstractFunction {
    static final String KEY = "currentUser()";
    static final String NAME = "currentUser";

    private static final Generator NULL_GEN = new StaticValueFunction(ValNull.INSTANCE).createGenerator();

    private Generator gen = NULL_GEN;

    public CurrentUser(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
    }

    @Override
    public void setStaticMappedValues(final Map<String, String> staticMappedValues) {
        final String v = staticMappedValues.get(KEY);
        if (v != null) {
            gen = new StaticValueFunction(ValString.create(v)).createGenerator();
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
}
