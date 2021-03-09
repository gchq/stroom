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
import java.text.ParseException;

@FunctionDef(
        name = Bottom.NAME,
        commonCategory = FunctionCategory.SELECTION,
        commonReturnType = ValString.class,
        signatures = @FunctionSignature(
                description = "Selects the bottom N values and returns them as a delimited string in the order they " +
                        "are read. E.g. for values [1, 2, 3, 4, 5], " + Bottom.NAME + "(${field}, ',', 2) returns " +
                        "'4,5'.",
                returnDescription = "The bottom N values as a delimited string.",
                args = {
                        @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function",
                                argType = Val.class),
                        @FunctionArg(
                                name = "delimiter",
                                description = "The delimiter string to use between each selected value, e.g. ', '.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "limit",
                                description = "The maximum number of values to included in the selection.",
                                argType = ValInteger.class)
                }))
public class Bottom extends AbstractSelectorFunction implements Serializable {

    static final String NAME = "bottom";
    private static final long serialVersionUID = -305845496003936297L;

    private String delimiter = "";
    private int limit = 10;

    public Bottom(final String name) {
        super(name, 3, 3);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        if (params.length >= 2) {
            delimiter = ParamParseUtil.parseStringParam(params, 1, name);
        }
        if (params.length >= 3) {
            limit = ParamParseUtil.parseIntParam(params, 2, name, true);
        }
        super.setParams(params);
    }

    @Override
    public Generator createGenerator() {
        return new BottomSelector(super.createGenerator(), delimiter, limit);
    }

    public static class BottomSelector extends Selector {

        private static final long serialVersionUID = 8153777070911899616L;

        private final String delimiter;
        private final int limit;

        BottomSelector(final Generator childGenerator, final String delimiter, final int limit) {
            super(childGenerator);
            this.delimiter = delimiter;
            this.limit = limit;
        }

        public Val select(final Selection<Val> selection) {
            final StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, selection.size() - limit); i < selection.size(); i++) {
                final Val val = selection.get(i);
                if (val.type().isValue()) {
                    if (sb.length() > 0) {
                        sb.append(delimiter);
                    }
                    sb.append(val.toString());
                }
            }
            return ValString.create(sb.toString());
        }

        public int getLimit() {
            return limit;
        }
    }
}
