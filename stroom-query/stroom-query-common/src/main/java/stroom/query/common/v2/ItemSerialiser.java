package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.Generator;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.function.Consumer;

public class ItemSerialiser {

    private final CompiledField[] fields;

    public ItemSerialiser(final CompiledField[] fields) {
        this.fields = fields;
    }

    private byte[] toBytes(final Consumer<Output> outputConsumer) {
        byte[] result;
        try (final Output output = new Output(100, 4096)) {
            outputConsumer.accept(output);
            output.flush();

            result = output.toBytes();
        }

        return result;
    }

    byte[] toBytes(final Generator[] generators) {
        return Metrics.measure("Item toBytes", () ->
                toBytes(output ->
                        writeGenerators(generators, output)));
    }

    Generator[] readGenerators(final byte[] bytes) {
        return Metrics.measure("Item readGenerators bytes", () -> {
            try (final Input input = new Input(bytes)) {
                return readGenerators(input);
            }
        });
    }

    private Generator[] readGenerators(final Input input) {
        return Metrics.measure("Item readGenerators", () -> {
            final Generator[] generators = new Generator[fields.length];
            int pos = 0;
            for (final CompiledField compiledField : fields) {
                final boolean nonNull = input.readBoolean();
                if (nonNull) {
                    final Expression expression = compiledField.getExpression();
                    final Generator generator = expression.createGenerator();
                    generator.read(input);
                    generators[pos] = generator;
                }
                pos++;
            }
            return generators;
        });
    }

    private void writeGenerators(final Generator[] generators, final Output output) {
        Metrics.measure("Item writeGenerators", () -> {
            if (generators.length > Byte.MAX_VALUE) {
                throw new RuntimeException("You can only write a maximum of " + 255 + " values");
            }
            for (final Generator generator : generators) {
                if (generator != null) {
                    output.writeBoolean(true);
                    generator.write(output);
                } else {
                    output.writeBoolean(false);
                }
            }
        });
    }
}
