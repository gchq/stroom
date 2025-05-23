package stroom.planb.impl.db;

import stroom.planb.shared.StateKeyType;
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

public class StateKeyTestUtil {

    public static List<ValueFunction> getValueFunctions() {
        return List.of(
                new ValueFunction(StateKeyType.BOOLEAN.name(), StateKeyType.BOOLEAN,
                        i -> ValBoolean.create(i > 0)),
                new ValueFunction(StateKeyType.BYTE.name(), StateKeyType.BYTE,
                        i -> ValByte.create(i.byteValue())),
                new ValueFunction(StateKeyType.SHORT.name(), StateKeyType.SHORT,
                        i -> ValShort.create(i.shortValue())),
                new ValueFunction(StateKeyType.INT.name(), StateKeyType.INT,
                        i -> ValInteger.create(i)),
                new ValueFunction(StateKeyType.LONG.name(), StateKeyType.LONG,
                        i -> ValLong.create(i.longValue())),
                new ValueFunction(StateKeyType.FLOAT.name(), StateKeyType.FLOAT,
                        i -> ValFloat.create(i.floatValue())),
                new ValueFunction(StateKeyType.DOUBLE.name(), StateKeyType.DOUBLE,
                        i -> ValDouble.create(i.doubleValue())),
                new ValueFunction(StateKeyType.STRING.name(), StateKeyType.STRING,
                        i -> ValString.create("test-" + i)),
                new ValueFunction(StateKeyType.UID_LOOKUP.name(), StateKeyType.UID_LOOKUP,
                        i -> ValString.create("test-" + i)),
                new ValueFunction(StateKeyType.HASH_LOOKUP.name(), StateKeyType.HASH_LOOKUP,
                        i -> ValString.create("test-" + i)),
                new ValueFunction(StateKeyType.VARIABLE.name(), StateKeyType.VARIABLE,
                        i -> ValString.create("test-" + i)),
                new ValueFunction("Variable mid", StateKeyType.VARIABLE,
                        i -> ValString.create(makeString(400))),
                new ValueFunction("Variable long", StateKeyType.VARIABLE,
                        i -> ValString.create(makeString(1000))));
    }

    public static String makeString(final int len) {
        final char[] chars = new char[len];
        Arrays.fill(chars, 'T');
        return new String(chars);
    }

    public record ValueFunction(String description,
                                StateKeyType stateValueType,
                                Function<Integer, Val> function) {

        @Override
        public String toString() {
            return description;
        }
    }

}
