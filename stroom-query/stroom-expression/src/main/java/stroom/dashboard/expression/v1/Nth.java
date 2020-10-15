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

import java.io.Serializable;
import java.text.ParseException;

class Nth extends AbstractSelectorFunction implements Serializable {
    static final String NAME = "nth";
    private static final long serialVersionUID = -305845496003936297L;

    private int pos;

    public Nth(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        if (params.length >= 2) {
            pos = ParamParseUtil.parseIntParam(params, 1, name, true);
            // Adjust for 0 based index.
            pos--;
        }
        super.setParams(params);
    }

    @Override
    public Generator createGenerator() {
        return new NthSelector(super.createGenerator(), pos);
    }

    private static class NthSelector extends Selector {
        private static final long serialVersionUID = 8153777070911899616L;

        private final int pos;

        NthSelector(final Generator childGenerator, final int pos) {
            super(childGenerator);
            this.pos = pos;
        }

        public Val select(final Generator[] subGenerators) {
            if (subGenerators.length > pos) {
                return subGenerators[pos].eval();
            }
            return ValNull.INSTANCE;
        }
    }
}
