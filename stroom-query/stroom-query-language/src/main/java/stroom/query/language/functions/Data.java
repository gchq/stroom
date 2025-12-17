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
        name = Data.NAME,
        commonCategory = FunctionCategory.LINK,
        commonReturnType = ValString.class,
        signatures = @FunctionSignature(
                description = "Produces a hyperlink for showing data within stroom.",
                returnDescription = "A hyperlink that will open a popup or tab showing the required data.",
                args = {
                        @FunctionArg(
                                name = "text",
                                description = "The link text to display.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = Data.ARG_ID,
                                description = "The ID of the stream.",
                                argType = ValLong.class),
                        @FunctionArg(
                                name = Data.ARG_PART_NO,
                                description = "The part number (one based). The part number is only applicable " +
                                        "for non-segmented streams (i.e uncooked streams). If a stream is segmented " +
                                        "or is not multi-part then the part number will be 1.",
                                argType = ValLong.class),
                        @FunctionArg(
                                name = Data.ARG_RECORD_NO,
                                description = "The record number (one based). The record number is only applicable " +
                                        "for segmented streams (i.e. cooked streams). Its value will be ignored for " +
                                        "non-segmented streams.",
                                isOptional = true,
                                argType = ValLong.class),
                        @FunctionArg(
                                name = Data.ARG_LINE_FROM,
                                description = "The line number of the start of the desired range of data " +
                                        "(inclusive, one based).",
                                isOptional = true,
                                argType = ValInteger.class),
                        @FunctionArg(
                                name = Data.ARG_COL_FROM,
                                description = "The column number of the start of the desired range of data " +
                                        "(inclusive, one based).",
                                isOptional = true,
                                argType = ValInteger.class),
                        @FunctionArg(
                                name = Data.ARG_LINE_TO,
                                description = "The line number of the end of the desired range of data " +
                                        "(inclusive, one based).",
                                isOptional = true,
                                argType = ValInteger.class),
                        @FunctionArg(
                                name = Data.ARG_COL_TO,
                                description = "The column number of the end of the desired range of data " +
                                        "(inclusive, one based).",
                                isOptional = true,
                                argType = ValInteger.class),
                        @FunctionArg(
                                name = Data.ARG_VIEW_TYPE,
                                description = "The view of the data to display. 'preview' shows a formatted portion " +
                                        "of the data starting, 'source' shows the un-formatted raw view of the data. " +
                                        "'info' shows the stream metadata. " +
                                        "Defaults to 'preview'.",
                                isOptional = true,
                                allowedValues = {"preview", "source", "info"},
                                argType = ValString.class),
                        @FunctionArg(
                                name = Data.ARG_DISPLAY_TYPE,
                                description = "How the data will be displayed in the user interface. Defaults to " +
                                        "'dialog'.",
                                isOptional = true,
                                allowedValues = {"dialog", "tab"},
                                argType = ValString.class),
                }))
class Data extends AbstractLink {

    static final String NAME = "data";

    static final String ARG_ID = "id";
    static final String ARG_PART_NO = "partNo";
    static final String ARG_RECORD_NO = "recordNo";
    static final String ARG_LINE_FROM = "lineFrom";
    static final String ARG_COL_FROM = "colFrom";
    static final String ARG_LINE_TO = "lineTo";
    static final String ARG_COL_TO = "colTo";
    static final String ARG_VIEW_TYPE = "viewType";
    static final String ARG_DISPLAY_TYPE = "displayType";

    public Data(final String name) {
        super(name, 2, 10);
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
            append(storedValues, sb, 4, ARG_LINE_FROM);
            append(storedValues, sb, 5, ARG_COL_FROM);
            append(storedValues, sb, 6, ARG_LINE_TO);
            append(storedValues, sb, 7, ARG_COL_TO);
            append(storedValues, sb, 8, ARG_VIEW_TYPE);
            append(storedValues, sb, 9, ARG_DISPLAY_TYPE);

            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            final String escaped = getEscapedString(val);
            return makeLink(
                    escaped,
                    EncodingUtil.encodeUrl(sb.toString()),
                    "data");
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
