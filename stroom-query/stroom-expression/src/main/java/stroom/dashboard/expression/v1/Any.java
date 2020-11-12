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

class Any extends AbstractSelectorFunction implements Serializable {
    static final String NAME = "any";
    private static final long serialVersionUID = -305845496003936297L;

    public Any(final String name) {
        super(name, 1, 1);
    }

    @Override
    public Generator createGenerator() {
        return new AnySelector(super.createGenerator());
    }

    private static class AnySelector extends Selector {
        private static final long serialVersionUID = 8153777070911899616L;
        private Val val;

        AnySelector(final Generator childGenerator) {
            super(childGenerator);
        }

        public Val select(Generator[] subGenerators) {
            if (this.val == null) {
                if (subGenerators.length > 0) {
                    for (final Generator generator : subGenerators) {
                        final Val val = generator.eval();
                        if (val.type().isValue()) {
                            this.val = val;
                            return this.val;
                        }
                    }
                }

                this.val = eval();
            }

            return this.val;
        }
    }
}
