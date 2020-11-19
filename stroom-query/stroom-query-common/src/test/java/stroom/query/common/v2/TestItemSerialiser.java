package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.query.api.v2.Field;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestItemSerialiser {
    @Test
    void test() {
        final FieldIndex fieldIndex = new FieldIndex();
        final List<Field> fields = new ArrayList<>();
        fields.add(new Field.Builder().expression("roundMinute(${EventTime})").build());
        fields.add(new Field.Builder().expression("${UserId}").build());
        fields.add(new Field.Builder().expression("count()").build());
        fields.add(new Field.Builder().expression("${StreamId}").build());
        fields.add(new Field.Builder().expression("${EventId}").build());
        final CompiledFields compiledFields = new CompiledFields(fields, fieldIndex, Map.of());

        final Generator[] generators = new Generator[fields.size()];
        for (int i = 0; i < generators.length; i++) {
            generators[i] = compiledFields.getField(i).getExpression().createGenerator();
        }

        final Val[] values = new Val[4];
        values[0] = ValString.create("2010-01-01T00:04:00.000Z");
        values[1] = ValString.create("user5");
        values[2] = ValNull.INSTANCE;
        values[3] = ValNull.INSTANCE;

        for (int count = 0; count < 295; count++) {
            for (int i = 0; i < generators.length; i++) {
                generators[i].set(values);
            }
        }

        final GroupKey key = new GroupKey(0, null, new Val[]{ValLong.create(1262304240000L), ValString.create("user5")});
        Item item = new Item(key, generators);
        String expected = item.toString();
        String actual;

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final Output output = new Output(baos)) {
            itemSerialiser.write(output, item);
        }

        final byte[] bytes = baos.toByteArray();

        try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
            final Item result = itemSerialiser.read(input);
            actual = result.toString();
        }

        assertThat(actual).isEqualTo(expected);


        // Try with some null values.
        generators[3] = null;
        generators[4] = null;
        item = new Item(key, generators);
        expected = item.toString();

        final ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        try (final Output output = new Output(baos2)) {
            itemSerialiser.write(output, item);
        }

        final byte[] bytes2 = baos2.toByteArray();

        try (final Input input = new Input(new ByteArrayInputStream(bytes2))) {
            final Item result = itemSerialiser.read(input);
            actual = result.toString();
        }

        assertThat(actual).isEqualTo(expected);
    }
}
