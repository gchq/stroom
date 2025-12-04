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
        name = DecodeUrl.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The input value with all URL encoding converted back to its plain text form.",
        signatures = @FunctionSignature(
                description = "Decodes a URL or other string that is the product of standard URL encoding, e.g. " +
                        "replacing '%20' with a space.",
                args = {
                        @FunctionArg(
                                name = "value",
                                description = "The URL or string to decode.",
                                argType = ValString.class)
                }))
class DecodeUrl extends AbstractStringFunction {

    static final String NAME = "decodeUrl";

    private static final Operation OPERATION = EncodingUtil::decodeUrl;

    public DecodeUrl(final String name) {
        super(name);
    }

    @Override
    Operation getOperation() {
        return OPERATION;
    }
}
