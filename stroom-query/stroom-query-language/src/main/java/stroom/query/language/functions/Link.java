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
        name = Link.NAME,
        commonCategory = FunctionCategory.LINK,
        commonReturnType = ValString.class,
        commonReturnDescription = "A Stroom syntax hyperlink string.",
        signatures = {
                @FunctionSignature(
                        description = "Creates a stroom syntax hyperlink string using the supplied URL.",
                        args = {
                                @FunctionArg(
                                        name = "url",
                                        description = "The URL to link to. The URL will be URL encoded.",
                                        argType = ValString.class)
                        }),
                @FunctionSignature(
                        description = "Creates a stroom syntax hyperlink string using the supplied link text and URL.",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The text to display on the link.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "url",
                                        description = "The URL to link to. The URL will be URL encoded.",
                                        argType = ValString.class)
                        }),
                @FunctionSignature(
                        description = "Creates a stroom syntax hyperlink string using the supplied link text, " +
                                      "URL and type.",
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        description = "The text to display on the link.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "url",
                                        description = "The URL to link to. The URL will be URL encoded.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "type",
                                        description = "The type of the url. To override the title of the tab/dialog " +
                                                      "being opened, append the title to the type, e.g. " +
                                                      "'dialog|My Title'.",
                                        argType = ValString.class,
                                        // taken from HyperLinkType
                                        allowedValues = {
                                                "tab", "dialog", "dashboard", "stepping", "data",
                                                "annotation", "browser"})})})
class Link extends AbstractLink {

    static final String NAME = "link";

    public Link(final String name) {
        super(name, 1, 3);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new LinkGen(childGenerators);
    }


    // --------------------------------------------------------------------------------


    private static final class LinkGen extends AbstractLinkGen {


        LinkGen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            Val link = ValNull.INSTANCE;

            if (childGenerators.length == 1) {
                final Val url = childGenerators[0].eval(storedValues, childDataSupplier);
                link = makeLink(url, url, ValNull.INSTANCE);
            } else if (childGenerators.length == 2) {
                final Val text = childGenerators[0].eval(storedValues, childDataSupplier);
                final Val url = childGenerators[1].eval(storedValues, childDataSupplier);
                link = makeLink(text, url, ValNull.INSTANCE);
            } else if (childGenerators.length == 3) {
                final Val text = childGenerators[0].eval(storedValues, childDataSupplier);
                final Val url = childGenerators[1].eval(storedValues, childDataSupplier);
                final Val type = childGenerators[2].eval(storedValues, childDataSupplier);
                link = makeLink(text, url, type);
            }

            return link;
        }
    }
}
