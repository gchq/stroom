package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.List;

public final class ValSerialisers {
    private ValSerialisers() {
        // Utility class.
    }

    public static void register(final Kryo kryo) {
        kryo.register(ValBoolean.class, new ValBooleanSerialiser(), 100);
        kryo.register(ValDouble.class, new ValDoubleSerialiser(), 101);
        kryo.register(ValInteger.class, new ValIntegerSerialiser(), 102);
        kryo.register(ValLong.class, new ValLongSerialiser(), 103);
        kryo.register(ValString.class, new ValStringSerialiser(), 104);
        kryo.register(ValNull.class, new ValNullSerialiser(), 105);
        kryo.register(ValErr.class, new ValErrSerialiser(), 106);
        kryo.register(GroupKey.class, new GroupKeySerialiser(), 200);
    }

    private static final class ValBooleanSerialiser extends Serializer<ValBoolean> {
        @Override
        public void write(final Kryo kryo, final Output output, final ValBoolean object) {
            output.writeBoolean(object.toBoolean());
        }

        @Override
        public ValBoolean read(final Kryo kryo, final Input input, final Class<ValBoolean> type) {
            return ValBoolean.create(input.readBoolean());
        }
    }

    private static final class ValDoubleSerialiser extends Serializer<ValDouble> {
        @Override
        public void write(final Kryo kryo, final Output output, final ValDouble object) {
            output.writeDouble(object.toDouble());
        }

        @Override
        public ValDouble read(final Kryo kryo, final Input input, final Class<ValDouble> type) {
            return ValDouble.create(input.readDouble());
        }
    }

    private static final class ValIntegerSerialiser extends Serializer<ValInteger> {
        @Override
        public void write(final Kryo kryo, final Output output, final ValInteger object) {
            output.writeInt(object.toInteger(), true);
        }

        @Override
        public ValInteger read(final Kryo kryo, final Input input, final Class<ValInteger> type) {
            return ValInteger.create(input.readInt(true));
        }
    }

    private static final class ValLongSerialiser extends Serializer<ValLong> {
        @Override
        public void write(final Kryo kryo, final Output output, final ValLong object) {
            output.writeLong(object.toLong(), true);
        }

        @Override
        public ValLong read(final Kryo kryo, final Input input, final Class<ValLong> type) {
            return ValLong.create(input.readLong(true));
        }
    }

    private static final class ValStringSerialiser extends Serializer<ValString> {
        @Override
        public void write(final Kryo kryo, final Output output, final ValString object) {
            output.writeString(object.toString());
        }

        @Override
        public ValString read(final Kryo kryo, final Input input, final Class<ValString> type) {
            return ValString.create(input.readString());
        }
    }

    private static final class ValNullSerialiser extends Serializer<ValNull> {
        @Override
        public void write(final Kryo kryo, final Output output, final ValNull object) {
        }

        @Override
        public ValNull read(final Kryo kryo, final Input input, final Class<ValNull> type) {
            return ValNull.INSTANCE;
        }
    }

    private static final class ValErrSerialiser extends Serializer<ValErr> {
        @Override
        public void write(final Kryo kryo, final Output output, final ValErr object) {
            output.writeString(object.getMessage());
        }

        @Override
        public ValErr read(final Kryo kryo, final Input input, final Class<ValErr> type) {
            final ValErr valErr = ValErr.create(input.readString());
            if (ValErr.INSTANCE.equals(valErr)) {
                return ValErr.INSTANCE;
            }
            return valErr;
        }
    }

    private static final class GroupKeySerialiser extends Serializer<GroupKey> {
        @Override
        public void write(final Kryo kryo, final Output output, final GroupKey object) {
            kryo.writeClassAndObject(output, object.getParent());
            final int size = object.getValues().size();
            output.writeInt(size, true);
            for (final Val val : object.getValues()) {
                kryo.writeClassAndObject(output, val);
            }
        }

        @Override
        public GroupKey read(final Kryo kryo, final Input input, final Class<GroupKey> type) {
            final GroupKey parent = (GroupKey) kryo.readClassAndObject(input);
            final int size = input.readInt(true);
            if (size == 1) {
                final Val val = (Val) kryo.readClassAndObject(input);
                return new GroupKey(parent, val);
            }

            final List<Val> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                final Val val = (Val) kryo.readClassAndObject(input);
                list.add(val);
            }
            return new GroupKey(parent, list);
        }
    }
}
