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

package stroom.dashboard.expression;

import java.text.ParseException;

public abstract class AbstractManyChildFunction extends AbstractFunction {
    Function[] functions;

    public AbstractManyChildFunction(final String name, final int minParams, final int maxParams) {
        super(name, minParams, maxParams);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);

        functions = new Function[params.length];
        for (int i = 0; i < params.length; i++) {
            final Object param = params[i];
            if (param instanceof Function) {
                final Function func = (Function) param;
                functions[i] = func;
            } else {
                functions[i] = new StaticValueFunction(param);
            }
        }
    }

    @Override
    public Generator createGenerator() {
        final Generator[] childGenerators = new Generator[functions.length];
        for (int i = 0; i < functions.length; i++) {
            childGenerators[i] = functions[i].createGenerator();
        }
        return createGenerator(childGenerators);
    }

    protected abstract Generator createGenerator(Generator[] childGenerators);

    @Override
    public boolean hasAggregate() {
        if (isAggregate()) {
            return true;
        }

        for (final Function function : functions) {
            if (function.hasAggregate()) {
                return true;
            }
        }

        return false;
    }
}
