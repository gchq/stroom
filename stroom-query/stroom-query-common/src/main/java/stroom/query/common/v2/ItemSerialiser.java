package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.GroupKeySerialiser;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class ItemSerialiser {
    private final CompiledFields fields;

    public ItemSerialiser(final CompiledFields fields) {
        this.fields = fields;
    }

    Item read(final Input input) {
        final GroupKey groupKey = GroupKeySerialiser.read(input);

        final Generator[] generators = new Generator[fields.size()];
        int pos = 0;
        for (final CompiledField compiledField : fields) {
            final Expression expression = compiledField.getExpression();
            final Generator generator = expression.createGenerator();
            generator.read(input);
            generators[pos] = generator;
            pos++;
        }

        return new Item(groupKey, generators);
    }

    void write(final Output output, final Item item) {
        if (item.generators.length > Byte.MAX_VALUE) {
            throw new RuntimeException("You can only write a maximum of " + 255 + " values");
        }

        GroupKeySerialiser.write(output, item.getKey());
        for (final Generator generator : item.getGenerators()) {
            generator.write(output);
        }
    }

    Item[] readArray(final Input input) {
        final int valueCount = input.readInt();
        final Item[] items = new Item[valueCount];
        for (int i = 0; i < valueCount; i++) {
            items[i] = read(input);
        }
        return items;
    }

    void writeArray(final Output output, final Item[] items) {
        output.writeInt(items.length);
        for (final Item item : items) {
            write(output, item);
        }
    }
}
