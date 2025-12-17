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

package stroom.planb.impl.db;

import stroom.planb.shared.StateValueType;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValByte;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValShort;
import stroom.query.language.functions.ValString;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class StateValueTestUtil {

    public static List<ValueFunction> getValueFunctions() {
        return List.of(
                new ValueFunction(StateValueType.BOOLEAN.name(), StateValueType.BOOLEAN,
                        i -> ValBoolean.create(i > 0)),
                new ValueFunction(StateValueType.BYTE.name(), StateValueType.BYTE,
                        i -> ValByte.create(i.byteValue())),
                new ValueFunction(StateValueType.SHORT.name(), StateValueType.SHORT,
                        i -> ValShort.create(i.shortValue())),
                new ValueFunction(StateValueType.INT.name(), StateValueType.INT,
                        i -> ValInteger.create(i)),
                new ValueFunction(StateValueType.LONG.name(), StateValueType.LONG,
                        i -> ValLong.create(i.longValue())),
                new ValueFunction(StateValueType.FLOAT.name(), StateValueType.FLOAT,
                        i -> ValFloat.create(i.floatValue())),
                new ValueFunction(StateValueType.DOUBLE.name(), StateValueType.DOUBLE,
                        i -> ValDouble.create(i.doubleValue())),
                new ValueFunction(StateValueType.STRING.name(), StateValueType.STRING,
                        i -> ValString.create("test-" + i)),
                new ValueFunction(StateValueType.UID_LOOKUP.name(), StateValueType.UID_LOOKUP,
                        i -> ValString.create("test-" + i)),
                new ValueFunction(StateValueType.HASH_LOOKUP.name(), StateValueType.HASH_LOOKUP,
                        i -> ValString.create("test-" + i)),
                new ValueFunction(StateValueType.VARIABLE.name(), StateValueType.VARIABLE,
                        i -> ValString.create("test-" + i)),
                new ValueFunction("Variable mid", StateValueType.VARIABLE,
                        i -> ValString.create(makeString(400))),
                new ValueFunction("Variable long", StateValueType.VARIABLE,
                        i -> ValString.create(makeString(1000))));
    }

    public static String makeString(final int len) {
        final char[] chars = new char[len];
        Arrays.fill(chars, 'T');
        return new String(chars);
    }

    public record ValueFunction(String description,
                                StateValueType stateValueType,
                                Function<Integer, Val> function) {

        @Override
        public String toString() {
            return description;
        }
    }

}
