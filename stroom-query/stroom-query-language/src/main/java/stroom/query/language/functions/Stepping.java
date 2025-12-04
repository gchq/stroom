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

import stroom.query.language.functions.ref.StoredValues;

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Stepping.NAME,
        commonCategory = FunctionCategory.LINK,
        commonReturnType = ValString.class,
        commonReturnDescription = "A hyperlink that will open the a stepping tab for the requested source data.",
        signatures = {
                @FunctionSignature(
                        description = "Produces a hyperlink for opening a stepping tab for the requested stream.",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The link text to display.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Stepping.ARG_ID,
                                        description = "The ID of the stream to step.",
                                        argType = ValLong.class)
                        }),
                @FunctionSignature(
                        description = "Produces a hyperlink for opening a stepping tab for the requested stream and " +
                                "part number.",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The link text to display.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Stepping.ARG_ID,
                                        description = "The ID of the stream to step.",
                                        argType = ValLong.class),
                                @FunctionArg(
                                        name = Stepping.ARG_PART_NO,
                                        description = "The part number to begin the stepping in (one based). The " +
                                                "part number is only applicable for non-segmented streams (i.e " +
                                                "uncooked streams). If a stream is segmented or is not multi-part " +
                                                "then the part number will be 1.",
                                        argType = ValLong.class)
                        }),
                @FunctionSignature(
                        description = "Produces a hyperlink for opening a stepping tab for the requested stream, " +
                                "part number and record number.",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The link text to display.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Stepping.ARG_ID,
                                        description = "The ID of the stream to step.",
                                        argType = ValLong.class),
                                @FunctionArg(
                                        name = Stepping.ARG_PART_NO,
                                        description = "The part number to begin the stepping in (one based). The " +
                                                "part number is only applicable for non-segmented streams (i.e " +
                                                "uncooked streams). If a stream is segmented or is not multi-part " +
                                                "then the part number will be 1.",
                                        argType = ValLong.class),
                                @FunctionArg(
                                        name = Stepping.ARG_RECORD_NO,
                                        description = "The record number to begin the stepping at (one based). The " +
                                                "record number is only applicable for segmented streams (i.e. " +
                                                "cooked streams). Its value will be ignored for non-segmented streams.",
                                        argType = ValLong.class)
                        })
        })
class Stepping extends AbstractLink {

    static final String NAME = "stepping";

    static final String ARG_ID = "id";
    static final String ARG_PART_NO = "partNo";
    static final String ARG_RECORD_NO = "recordNo";

    public Stepping(final String name) {
        super(name, 1, 3);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    private static final class Gen extends AbstractLinkGen {


        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final StringBuilder sb = new StringBuilder();

            append(storedValues, sb, 1, ARG_ID);
            append(storedValues, sb, 2, ARG_PART_NO);
            append(storedValues, sb, 3, ARG_RECORD_NO);

            return makeLink(
                    getEscapedString(childGenerators[0].eval(storedValues, childDataSupplier)),
                    EncodingUtil.encodeUrl(sb.toString()),
                    "stepping");
        }

        private void append(final StoredValues storedValues,
                            final StringBuilder sb,
                            final int index,
                            final String key) {
            if (index < childGenerators.length) {
                final Val val = childGenerators[index].eval(storedValues, null);
                if (val.type().isValue()) {
                    if (sb.length() > 0) {
                        sb.append("&");
                    } else {
                        sb.append("?");
                    }
                    sb.append(key);
                    sb.append("=");
                    sb.append(getEscapedString(val));
                }
            }
        }
    }
}
