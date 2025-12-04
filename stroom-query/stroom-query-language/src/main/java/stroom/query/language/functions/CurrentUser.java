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

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CurrentUser.NAME,
        commonCategory = FunctionCategory.PARAM,
        commonReturnType = ValString.class,
        commonReturnDescription = "Display name (or subjectId if there isn't one) of the logged in user.",
        signatures = {
                @FunctionSignature(
                        description = "Returns the display name (or subjectId if there isn't one) of the current " +
                                      "logged in user.",
                        args = {})
        })
class CurrentUser extends AbstractCurrentUser {

    static final String NAME = "currentUser";
    static final String KEY = NAME + "()";

    public CurrentUser(final String name) {
        super(name);
    }

    @Override
    String getKey() {
        return KEY;
    }
}
