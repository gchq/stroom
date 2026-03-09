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

import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.shared.KeyType;
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

public class StateKeyTestUtil {

    public static List<ValueFunction> getValueFunctions() {
        return List.of(
                new ValueFunction(KeyType.BOOLEAN.name(), KeyType.BOOLEAN,
                        i -> KeyPrefix.create(ValBoolean.create(i > 0))),
                new ValueFunction(KeyType.BYTE.name(), KeyType.BYTE,
                        i -> KeyPrefix.create(ValByte.create(i.byteValue()))),
                new ValueFunction(KeyType.SHORT.name(), KeyType.SHORT,
                        i -> KeyPrefix.create(ValShort.create(i.shortValue()))),
                new ValueFunction(KeyType.INT.name(), KeyType.INT,
                        i -> KeyPrefix.create(ValInteger.create(i))),
                new ValueFunction(KeyType.LONG.name(), KeyType.LONG,
                        i -> KeyPrefix.create(ValLong.create(i.longValue()))),
                new ValueFunction(KeyType.FLOAT.name(), KeyType.FLOAT,
                        i -> KeyPrefix.create(ValFloat.create(i.floatValue()))),
                new ValueFunction(KeyType.DOUBLE.name(), KeyType.DOUBLE,
                        i -> KeyPrefix.create(ValDouble.create(i.doubleValue()))),
                new ValueFunction(KeyType.STRING.name(), KeyType.STRING,
                        i -> KeyPrefix.create(ValString.create("test-" + i))),
                new ValueFunction(KeyType.UID_LOOKUP.name(), KeyType.UID_LOOKUP,
                        i -> KeyPrefix.create(ValString.create("test-" + i))),
                new ValueFunction(KeyType.HASH_LOOKUP.name(), KeyType.HASH_LOOKUP,
                        i -> KeyPrefix.create(ValString.create("test-" + i))),
                new ValueFunction(KeyType.VARIABLE.name(), KeyType.VARIABLE,
                        i -> KeyPrefix.create(ValString.create("test-" + i))),
                new ValueFunction("Variable mid", KeyType.VARIABLE,
                        i -> KeyPrefix.create(ValString.create(makeString(400)))),
                new ValueFunction("Variable long", KeyType.VARIABLE,
                        i -> KeyPrefix.create(ValString.create(makeString(1000)))));
    }

    public static String makeString(final int len) {
        final char[] chars = new char[len];
        Arrays.fill(chars, 'T');
        return new String(chars);
    }

    public record ValueFunction(String description,
                                KeyType stateValueType,
                                Function<Integer, KeyPrefix> function) {

        @Override
        public String toString() {
            return description;
        }
    }

}
