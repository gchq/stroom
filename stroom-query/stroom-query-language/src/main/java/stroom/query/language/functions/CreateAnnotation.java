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

/**
 * See also HyperlinkEventHandlerImpl
 */
@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CreateAnnotation.NAME,
        commonCategory = FunctionCategory.LINK,
        commonReturnType = ValString.class,
        signatures = {
                @FunctionSignature(
                        returnDescription = "Forms a hyperlink to create a new annotation.",
                        description = "Creates a hyperlink that will open the annotation edit screen pre-populated " +
                                      "with the supplied values ready to create a new annotation.",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The displayed text of the annotation link.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = CreateAnnotation.ARG_TITLE,
                                        description = "The initial title of the annotation.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = CreateAnnotation.ARG_SUBJECT,
                                        description = "The initial subject of the annotation.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = CreateAnnotation.ARG_STATUS,
                                        description = "The initial status of the annotation.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = CreateAnnotation.ARG_ASSIGNED_TO,
                                        description = "The initial username of the user that this annotation will be " +
                                                      "assigned to.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = CreateAnnotation.ARG_COMMENT,
                                        description = "A initial comment for this annotation.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = CreateAnnotation.ARG_EVENT_ID_LIST,
                                        description = "A comma separated list of event ids to link the new " +
                                                      "annotation to, e.g. 1:1,1:5,2:4.",
                                        isOptional = true,
                                        argType = ValString.class),
                        })
        })
class CreateAnnotation extends AbstractLink {

    static final String ARG_TITLE = "title";
    static final String ARG_SUBJECT = "subject";
    static final String ARG_STATUS = "status";
    static final String ARG_ASSIGNED_TO = "assignedTo";
    static final String ARG_COMMENT = "comment";
    static final String ARG_EVENT_ID_LIST = "eventIdList";

    static final String NAME = "createAnnotation";

    public CreateAnnotation(final String name) {
        super(name, 1, 7);
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
            append(storedValues, sb, 1, ARG_TITLE);
            append(storedValues, sb, 2, ARG_SUBJECT);
            append(storedValues, sb, 3, ARG_STATUS);
            append(storedValues, sb, 4, ARG_ASSIGNED_TO);
            append(storedValues, sb, 5, ARG_COMMENT);
            append(storedValues, sb, 6, ARG_EVENT_ID_LIST);

            return makeLink(getEscapedString(
                            childGenerators[0].eval(storedValues, childDataSupplier)),
                    EncodingUtil.encodeUrl(sb.toString()),
                    "annotation");
        }

        private void append(final StoredValues storedValues,
                            final StringBuilder sb,
                            final int index,
                            final String key) {
            if (index < childGenerators.length) {
                final Val val = childGenerators[index].eval(storedValues, null);
                if (val.type().isValue()) {
                    if (!sb.isEmpty()) {
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
