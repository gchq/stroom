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
        name = Dashboard.NAME,
        commonCategory = FunctionCategory.LINK,
        commonReturnType = ValString.class,
        commonReturnDescription = "A hyperlink that will open a dashboard.",
        commonDescription = "Produces a hyperlink for opening a specific dashboard.",
        signatures = {
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        argType = ValString.class,
                                        description = "The text that the hyperlink will display."),
                                @FunctionArg(
                                        name = "uuid",
                                        argType = ValString.class,
                                        description = "The UUID for the dashboard to link to.")}),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        argType = ValString.class,
                                        description = "The text that the hyperlink will display."),
                                @FunctionArg(
                                        name = "uuid",
                                        argType = ValString.class,
                                        description = "The UUID for the dashboard to link to."),
                                @FunctionArg(
                                        name = "params",
                                        argType = ValString.class,
                                        description = "A String of space separated parameters to pass into the " +
                                                      "dashboard, e.g. 'userId=user1 building=hq'"),
                        }),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "text",
                                        argType = ValString.class,
                                        description = "The text that the hyperlink will display."),
                                @FunctionArg(
                                        name = "uuid",
                                        argType = ValString.class,
                                        description = "The UUID for the dashboard to link to."),
                                @FunctionArg(
                                        name = "params",
                                        argType = ValString.class,
                                        description = "A String of space separated parameters to pass into the " +
                                                "dashboard, e.g. 'userId=user1 building=hq'"),
                                @FunctionArg(
                                        name = "target",
                                        argType = ValString.class,
                                        description = "A String defining where the link will open by default " +
                                                      "('self' or 'tab')")
                        })})
class Dashboard extends AbstractLink {

    static final String NAME = "dashboard";

    public Dashboard(final String name) {
        super(name, 2, 4);
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
            Val link = ValNull.INSTANCE;

            if (childGenerators.length == 2) {
                final Val text = childGenerators[0].eval(storedValues, childDataSupplier);
                final Val uuid = childGenerators[1].eval(storedValues, childDataSupplier);
                link = makeDashboardLink(text, uuid, ValNull.INSTANCE, ValNull.INSTANCE);
            } else if (childGenerators.length == 3) {
                final Val text = childGenerators[0].eval(storedValues, childDataSupplier);
                final Val uuid = childGenerators[1].eval(storedValues, childDataSupplier);
                final Val params = childGenerators[2].eval(storedValues, childDataSupplier);
                link = makeDashboardLink(text, uuid, params, ValNull.INSTANCE);
            } else if (childGenerators.length == 4) {
                final Val text = childGenerators[0].eval(storedValues, childDataSupplier);
                final Val uuid = childGenerators[1].eval(storedValues, childDataSupplier);
                final Val params = childGenerators[2].eval(storedValues, childDataSupplier);
                final Val target = childGenerators[3].eval(storedValues, childDataSupplier);
                link = makeDashboardLink(text, uuid, params, target);
            }

            return link;
        }

        private Val makeDashboardLink(final Val text, final Val uuid, final Val params, final Val target) {
            if (text.type().isError()) {
                return text;
            }
            if (uuid.type().isError()) {
                return uuid;
            }
            if (params.type().isError()) {
                return params;
            }

            final StringBuilder url = new StringBuilder();
            url.append("?uuid=");
            url.append(getEscapedString(uuid));
            if (params.type().isValue()) {
                url.append("&params=");
                url.append(getEscapedString(params));
            }

            return makeLink(getEscapedString(text), EncodingUtil.encodeUrl(url.toString()), "dashboard",
                    target.toString());
        }
    }
}
