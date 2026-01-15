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

package stroom.query.api;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.ArrayList;
import java.util.List;

public enum ConditionalFormattingStyle implements HasDisplayValue, HasPrimitiveValue {
    NONE("None", "", 0),
    RED("Red", "cf-red", 1),
    PINK("Pink", "cf-pink", 2),
    PURPLE("Purple", "cf-purple", 3),
    DEEP_PURPLE("Deep Purple", "cf-deep-purple", 4),
    INDIGO("Indigo", "cf-indigo", 5),
    BLUE("Blue", "cf-blue", 6),
    LIGHT_BLUE("Light Blue", "cf-light-blue", 7),
    CYAN("Cyan", "cf-cyan", 8),
    TEAL("Teal", "cf-teal", 9),
    GREEN("Green", "cf-green", 10),
    LIGHT_GREEN("Light Green", "cf-light-green", 11),
    LIME("Lime", "cf-lime", 12),
    YELLOW("Yellow", "cf-yellow", 13),
    AMBER("Amber", "cf-amber", 14),
    ORANGE("Orange", "cf-orange", 15),
    DEEP_ORANGE("Deep Orange", "cf-deep-orange", 16),
    BROWN("Brown", "cf-brown", 17),
    GREY("Grey", "cf-grey", 18),
    BLUE_GREY("Blue Grey", "cf-blue-grey", 19),
    ;

    public static final List<ConditionalFormattingStyle> LIST = new ArrayList<>();

    static {
        LIST.add(NONE);
        LIST.add(RED);
        LIST.add(PINK);
        LIST.add(PURPLE);
        LIST.add(DEEP_PURPLE);
        LIST.add(INDIGO);
        LIST.add(BLUE);
        LIST.add(LIGHT_BLUE);
        LIST.add(CYAN);
        LIST.add(TEAL);
        LIST.add(GREEN);
        LIST.add(LIGHT_GREEN);
        LIST.add(LIME);
        LIST.add(YELLOW);
        LIST.add(AMBER);
        LIST.add(ORANGE);
        LIST.add(DEEP_ORANGE);
        LIST.add(BROWN);
        LIST.add(GREY);
        LIST.add(BLUE_GREY);
    }

    public static final PrimitiveValueConverter<ConditionalFormattingStyle> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(ConditionalFormattingStyle.class, ConditionalFormattingStyle.values());
    private final String displayValue;
    private final byte primitiveValue;
    private final String cssClassName;

    ConditionalFormattingStyle(final String displayValue,
                               final String cssClassName,
                               final int primitiveValue) {
        this.displayValue = displayValue;
        this.cssClassName = cssClassName;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getCssClassName() {
        return cssClassName;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
