/*
 * Copyright 2024 Crown Copyright
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

import stroom.query.language.token.Param;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIKey;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CurrentUser.NAME,
        commonCategory = FunctionCategory.VALUE,
        commonReturnType = ValString.class,
        commonReturnDescription = "Display name of the logged in user.",
        signatures = {
                @FunctionSignature(
                        description = "Returns the display name of the current logged in user.",
                        args = {}),
                @FunctionSignature(
                        description = "Returns the name of the current logged in user in the form of the " +
                                "supplied nameType.",
                        args = {
                                @FunctionArg(
                                        name = "nameType",
                                        description =
                                                "The type of name to return. One of ('display'|'full'|'subject'). "
                                                        + "'display' returns the display name of the user. " +
                                                        "'full' returns the full name of the user. " +
                                                        "'subject returns the unique identity of the user.",
                                        argType = ValString.class)
                        })
        })
class CurrentUser extends AbstractFunction {

    static final String NAME = "currentUser";

    private Generator gen = Null.GEN;
    private Param[] params = null;

    public CurrentUser(final String name) {
        super(name, 0, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
        this.params = params;
    }

    @Override
    public void setStaticMappedValues(final Map<CIKey, String> staticMappedValues) {
        final String v = switch (getNameType()) {
            case DISPLAY_NAME -> staticMappedValues.get(ParamKeys.CURRENT_USER_KEY);
            case SUBJECT_ID -> staticMappedValues.get(ParamKeys.CURRENT_USER_SUBJECT_ID_KEY);
            case FULL_NAME -> staticMappedValues.get(ParamKeys.CURRENT_USER_FULL_NAME_KEY);
        };
        if (v != null) {
            gen = new StaticValueGen(ValString.create(v));
        }
    }

    @Override
    public Generator createGenerator() {
        return gen;
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    private NameType getNameType() {
        if (params == null || params.length == 0) {
            return NameType.DEFAULT;
        } else {
            return NameType.fromString(params[0].toString());
        }
    }


    // --------------------------------------------------------------------------------


    private enum NameType {
        DISPLAY_NAME("display"),
        SUBJECT_ID("subject"),
        FULL_NAME("full"),
        ;
        private static final Map<CIKey, NameType> STR_TO_ENUM_MAP = new HashMap<>(3);
        private static final NameType DEFAULT = DISPLAY_NAME;

        static {
            for (final NameType nameType : NameType.values()) {
                STR_TO_ENUM_MAP.put(CIKey.of(nameType.paramVal, ParamKeys.KNOWN_KEYS_MAP), nameType);
            }
        }

        private final String paramVal;

        NameType(final String paramVal) {
            this.paramVal = paramVal;
        }

        static NameType fromString(final String type) {
            return GwtNullSafe.getOrElse(
                    type,
                    type2 -> STR_TO_ENUM_MAP.get(CIKey.of(type2, ParamKeys.KNOWN_KEYS_MAP)),
                    DEFAULT);
        }
    }
}
