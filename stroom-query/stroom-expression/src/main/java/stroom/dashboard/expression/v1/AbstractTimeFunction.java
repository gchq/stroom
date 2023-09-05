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

abstract class AbstractTimeFunction extends AbstractFunction {

    public AbstractTimeFunction(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    @Override
    public boolean requiresChildData() {
        return false;
    }

    @Override
    public Type getCommonReturnType() {
        return Type.DATE;
    }
}
