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

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);

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
            for (final Generator generator : generators) {
                generator.set(values);
            }
        }

        final Key key = Key
                .root()
                .resolve(new GroupKeyPart(new Val[]{ValLong.create(1262304240000L), ValString.create("user5")}));
        byte[] keyBytes = itemSerialiser.toBytes(key);
        byte[] generatorBytes = itemSerialiser.toBytes(generators);
        final RawItem item = new RawItem(keyBytes, generatorBytes);
        final String expected = toString(generators);


        final byte[] bytes = itemSerialiser.toBytes(item);
        final RawItem result = itemSerialiser.readRawItem(bytes);
        final Generator[] generators1 = itemSerialiser.readGenerators(result.getGenerators());
        final String actual = toString(generators1);

        assertThat(actual).isEqualTo(expected);

        // Try with some null values.
        generators[3] = null;
        generators[4] = null;
        keyBytes = itemSerialiser.toBytes(key);
        generatorBytes = itemSerialiser.toBytes(generators);
        final RawItem item2 = new RawItem(keyBytes, generatorBytes);
        final String expected2 = toString(generators);

        final byte[] bytes2 = itemSerialiser.toBytes(item2);
        final RawItem result2 = itemSerialiser.readRawItem(bytes2);
        final Generator[] generators2 = itemSerialiser.readGenerators(result2.getGenerators());
        final String actual2 = toString(generators2);

        assertThat(actual2).isEqualTo(expected2);
    }

    public String toString(Generator[] generators) {
        final StringBuilder sb = new StringBuilder();
        for (final Generator value : generators) {
            if (value != null) {
                try {
                    sb.append(value.eval().toString());
                } catch (final RuntimeException e) {
                    // if the evaluation of the generator fails record the class of the exception
                    // so we can see which one has a problem
                    sb.append(e.getClass().getCanonicalName());
                }
            } else {
                sb.append("null");
            }
            sb.append("\t");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
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

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);

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
            for (final Generator generator : generators) {
                generator.set(values);
            }
        }

        final Key key = Key.root();
        byte[] keyBytes = itemSerialiser.toBytes(key);
        byte[] generatorBytes = itemSerialiser.toBytes(generators);
        final RawItem item = new RawItem(keyBytes, generatorBytes);
        final String expected = toString(generators);

        final byte[] bytes = itemSerialiser.toBytes(item);
        final RawItem result = itemSerialiser.readRawItem(bytes);
        final Generator[] generators1 = itemSerialiser.readGenerators(result.getGenerators());
        final String actual = toString(generators1);

        assertThat(actual).isEqualTo(expected);

        // Try with some null values.
        generators[3] = null;
        generators[4] = null;
        keyBytes = itemSerialiser.toBytes(key);
        generatorBytes = itemSerialiser.toBytes(generators);
        final RawItem item2 = new RawItem(keyBytes, generatorBytes);
        final String expected2 = toString(generators);

        final byte[] bytes2 = itemSerialiser.toBytes(item2);
        final RawItem result2 = itemSerialiser.readRawItem(bytes2);
        final Generator[] generators2 = itemSerialiser.readGenerators(result2.getGenerators());
        final String actual2 = toString(generators2);

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

        final ItemSerialiser itemSerialiser = new ItemSerialiser(compiledFields);

        final Generator[] generators = new Generator[fields.size()];

        final Key key = Key.root();
        byte[] keyBytes = itemSerialiser.toBytes(key);
        byte[] generatorBytes = itemSerialiser.toBytes(generators);
        final RawItem item = new RawItem(keyBytes, generatorBytes);
        final String expected = toString(generators);

        final byte[] bytes = itemSerialiser.toBytes(item);
        final RawItem result = itemSerialiser.readRawItem(bytes);
        final Generator[] generators1 = itemSerialiser.readGenerators(result.getGenerators());
        final String actual = toString(generators1);

        assertThat(actual).isEqualTo(expected);

        // Try with some null values.
        generators[3] = null;
        generators[4] = null;
        keyBytes = itemSerialiser.toBytes(key);
        generatorBytes = itemSerialiser.toBytes(generators);
        final RawItem item2 = new RawItem(keyBytes, generatorBytes);
        final String expected2 = toString(generators);

        final byte[] bytes2 = itemSerialiser.toBytes(item2);
        final RawItem result2 = itemSerialiser.readRawItem(bytes2);
        final Generator[] generators2 = itemSerialiser.readGenerators(result2.getGenerators());
        final String actual2 = toString(generators2);

        assertThat(actual2).isEqualTo(expected2);
    }
}
