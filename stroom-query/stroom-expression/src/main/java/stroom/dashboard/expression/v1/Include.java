/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.expression.v1;

import java.io.Serializable;

class Include extends AbstractIncludeExclude implements Serializable {
    static final String NAME = "include";
    private static final long serialVersionUID = -305845496003936297L;

    public Include(final String name) {
        super(name);
    }

    @Override
    boolean inverse() {
        return false;
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    private static final class Gen extends AbstractGen {
        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        boolean inverse() {
            return false;
        }
    }
}
