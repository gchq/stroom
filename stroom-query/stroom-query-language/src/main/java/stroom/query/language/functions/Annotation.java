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

// TODO @AT Confirm behavior of annotation link in the app, i.e. can you have a link with no anno id and/or no linked
//   event. Need to confirm which args are opt.

import stroom.query.language.functions.ref.StoredValues;

import java.util.function.Supplier;

/**
 * See also HyperlinkEventHandlerImpl
 */

@Deprecated // Users are no longer expected to use this function to create or edit annotations
@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Annotation.NAME,
        commonCategory = FunctionCategory.LINK,
        commonReturnType = ValString.class,
        signatures = {
                @FunctionSignature(
                        returnDescription = "A hyperlink to open the annotation edit screen for an existing " +
                                            "annotation.",
                        description = "Creates a hyperlink that will open the annotation edit screen showing " +
                                      "the existing annotation with the supplied " + Annotation.ARG_ANNOTATION_ID + ".",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The displayed text of the annotation link.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Annotation.ARG_ANNOTATION_ID,
                                        description = "The ID of the annotation.",
                                        argType = ValString.class,
                                        defaultValue = "${annotation:Id}")}),
                @FunctionSignature(
                        returnDescription = "A hyperlink to open the annotation edit screen.",
                        description = "Creates a hyperlink that will open the annotation edit screen showing the " +
                                      "existing annotation with the supplied " + Annotation.ARG_ANNOTATION_ID +
                                      " or if that is '' or null() then it will show the edit screen pre-populated " +
                                      "with the supplied values ready to create a new annotation.",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The displayed text of the annotation link.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Annotation.ARG_ANNOTATION_ID,
                                        description = "The ID of the existing annotation or null() if creating " +
                                                      "a new one.",
                                        argType = ValString.class,
                                        defaultValue = "${annotation:Id}"),
                                @FunctionArg(
                                        name = Annotation.ARG_STREAM_ID,
                                        description = "The ID of the stream of the linked event. Must be provided " +
                                                      "if no " + Annotation.ARG_ANNOTATION_ID + " is provided.",
                                        isOptional = true,
                                        argType = ValString.class,
                                        defaultValue = "${StreamId}"),
                                @FunctionArg(
                                        name = Annotation.ARG_EVENT_ID,
                                        description = "The ID of the of the linked event. Must be provided " +
                                                      "if no " + Annotation.ARG_ANNOTATION_ID + " is provided.",
                                        isOptional = true,
                                        argType = ValString.class,
                                        defaultValue = "${EventId}"),
                                @FunctionArg(
                                        name = Annotation.ARG_TITLE,
                                        description = "The title of the annotation",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Annotation.ARG_SUBJECT,
                                        description = "The subject of the annotation.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Annotation.ARG_STATUS,
                                        description = "The status of the annotation (see " +
                                                      "stroom.annotation.statusValues property for possible values.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Annotation.ARG_ASSIGNED_TO,
                                        description = "The username of the user that this annotation will be " +
                                                      "assigned to.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Annotation.ARG_COMMENT,
                                        description = "A comment for this annotation.",
                                        isOptional = true,
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = Annotation.ARG_EVENT_ID_LIST,
                                        description = "A comma separated list of event ids, e.g. 1:1,1:5,2:4.",
                                        isOptional = true,
                                        argType = ValString.class),
                        })
        })
class Annotation extends AbstractLink {

    static final String ARG_ANNOTATION_ID = "annotationId";
    static final String ARG_STREAM_ID = "StreamId";
    static final String ARG_EVENT_ID = "EventId";
    static final String ARG_TITLE = "title";
    static final String ARG_SUBJECT = "subject";
    static final String ARG_STATUS = "status";
    static final String ARG_ASSIGNED_TO = "assignedTo";
    static final String ARG_COMMENT = "comment";
    static final String ARG_EVENT_ID_LIST = "eventIdList";

    static final String NAME = "annotation";

    public Annotation(final String name) {
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
            append(storedValues, sb, 1, ARG_ANNOTATION_ID);
            append(storedValues, sb, 2, ARG_STREAM_ID);
            append(storedValues, sb, 3, ARG_EVENT_ID);
            append(storedValues, sb, 4, ARG_TITLE);
            append(storedValues, sb, 5, ARG_SUBJECT);
            append(storedValues, sb, 6, ARG_STATUS);
            append(storedValues, sb, 7, ARG_ASSIGNED_TO);
            append(storedValues, sb, 8, ARG_COMMENT);
            append(storedValues, sb, 9, ARG_EVENT_ID_LIST);

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
