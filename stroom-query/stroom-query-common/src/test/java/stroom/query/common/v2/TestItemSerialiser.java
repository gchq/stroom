package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.query.api.v2.Field;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestItemSerialiser {
    @Test
    void test() {
        final FieldIndex fieldIndex = new FieldIndex();
        final List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().expression("roundMinute(${EventTime})").build());
        fields.add(Field.builder().expression("${UserId}").build());
        fields.add(Field.builder().expression("count()").build());
        fields.add(Field.builder().expression("${StreamId}").build());
        fields.add(Field.builder().expression("${EventId}").build());
        final CompiledField[] compiledFields = CompiledFields.create(fields, fieldIndex, Map.of());

        final Generator[] generators = new Generator[fields.size()];
        for (int i = 0; i < generators.length; i++) {
            generators[i] = compiledFields[i].getExpression().createGenerator();
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

        final Key key = new Key(List.of(new GroupKeyPart(new Val[]{ValLong.create(1262304240000L), ValString.create("user5")})));
        final RawKey rawKey = key.toRawKey();
        final Item item = new Item(rawKey, generators);
        final String expected = item.toString();

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);
        final byte[] bytes = itemSerialiser.toBytes(item);
        final Item result = itemSerialiser.readItem(bytes);
        final String actual = result.toString();

        assertThat(actual).isEqualTo(expected);

        // Try with some null values.
        generators[3] = null;
        generators[4] = null;
        final Item item2 = new Item(rawKey, generators);
        final String expected2 = item2.toString();

        final byte[] bytes2 = itemSerialiser.toBytes(item2);
        final Item result2 = itemSerialiser.readItem(bytes2);
        final String actual2 = result2.toString();

        assertThat(actual2).isEqualTo(expected2);
    }

    @Test
    void testRoot() {
        final FieldIndex fieldIndex = new FieldIndex();
        final List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().expression("roundMinute(${EventTime})").build());
        fields.add(Field.builder().expression("${UserId}").build());
        fields.add(Field.builder().expression("count()").build());
        fields.add(Field.builder().expression("${StreamId}").build());
        fields.add(Field.builder().expression("${EventId}").build());
        final CompiledField[] compiledFields = CompiledFields.create(fields, fieldIndex, Map.of());

        final Generator[] generators = new Generator[fields.size()];
        for (int i = 0; i < generators.length; i++) {
            generators[i] = compiledFields[i].getExpression().createGenerator();
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

        final Key key = new Key(Collections.emptyList());
        final RawKey rawKey = key.toRawKey();
        final Item item = new Item(rawKey, generators);
        final String expected = item.toString();

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);
        final byte[] bytes = itemSerialiser.toBytes(item);
        final Item result = itemSerialiser.readItem(bytes);
        final String actual = result.toString();

        assertThat(actual).isEqualTo(expected);

        // Try with some null values.
        generators[3] = null;
        generators[4] = null;
        final Item item2 = new Item(rawKey, generators);
        final String expected2 = item2.toString();

        final byte[] bytes2 = itemSerialiser.toBytes(item2);
        final Item result2 = itemSerialiser.readItem(bytes2);
        final String actual2 = result2.toString();

        assertThat(actual2).isEqualTo(expected2);
    }

    @Test
    void testNull() {
        final FieldIndex fieldIndex = new FieldIndex();
        final List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().expression("roundMinute(${EventTime})").build());
        fields.add(Field.builder().expression("${UserId}").build());
        fields.add(Field.builder().expression("count()").build());
        fields.add(Field.builder().expression("${StreamId}").build());
        fields.add(Field.builder().expression("${EventId}").build());
        final CompiledField[] compiledFields = CompiledFields.create(fields, fieldIndex, Map.of());

        final Generator[] generators = new Generator[fields.size()];

        final Key key = new Key(Collections.emptyList());
        final RawKey rawKey = key.toRawKey();
        final Item item = new Item(rawKey, generators);
        final String expected = item.toString();

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);
        final byte[] bytes = itemSerialiser.toBytes(item);
        final Item result = itemSerialiser.readItem(bytes);
        final String actual = result.toString();

        assertThat(actual).isEqualTo(expected);

        // Try with some null values.
        generators[3] = null;
        generators[4] = null;
        final Item item2 = new Item(rawKey, generators);
        final String expected2 = item2.toString();

        final byte[] bytes2 = itemSerialiser.toBytes(item2);
        final Item result2 = itemSerialiser.readItem(bytes2);
        final String actual2 = result2.toString();

        assertThat(actual2).isEqualTo(expected2);
    }
}
