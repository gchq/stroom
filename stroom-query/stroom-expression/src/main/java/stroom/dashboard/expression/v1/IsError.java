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

class IsError extends AbstractIsFunction implements Serializable {
    static final String NAME = "isError";
    private static final long serialVersionUID = -305245496413936297L;
    private static final ErrorTest TEST = new ErrorTest();

    public IsError(final String name) {
        super(name);
    }

    @Override
    Test getTest() {
        return TEST;
    }

    private static class ErrorTest implements Test {
        @Override
        public Val test(final Val val) {
            return ValBoolean.create(val.type().isError());
        }
    }
}
