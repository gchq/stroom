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

class IsString extends AbstractIsFunction implements Serializable {
    static final String NAME = "isString";
    private static final long serialVersionUID = -305145496413936297L;
    private static final StringTest TEST = new StringTest();

    public IsString(final String name) {
        super(name);
    }

    @Override
    Test getTest() {
        return TEST;
    }

    private static class StringTest implements Test {
        @Override
        public Val test(final Val val) {
            return ValBoolean.create(val instanceof ValString);
        }
    }
}
