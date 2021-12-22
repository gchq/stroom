package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.Generator;
import stroom.util.io.ByteSizeUnit;
import stroom.util.logging.Metrics;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Generators {

    private static final int MIN_VALUE_SIZE = (int) ByteSizeUnit.KIBIBYTE.longBytes(1);
    private static final int MAX_VALUE_SIZE = (int) ByteSizeUnit.MEBIBYTE.longBytes(1);

    private final CompiledField[] fields;
    private byte[] bytes;
    private Generator[] generators;

    public Generators(final CompiledField[] fields, final Generator[] generators) {
        this.fields = fields;
        this.generators = generators;
    }

    public Generators(final CompiledField[] fields, final byte[] bytes) {
        this.fields = fields;
        this.bytes = bytes;
    }

    byte[] getBytes() {
        if (bytes == null) {
            Metrics.measure("Item toBytes", () -> {
                try (final Output output = new Output(MIN_VALUE_SIZE, MAX_VALUE_SIZE)) {
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
                    output.flush();
                    bytes = output.toBytes();
                }
            });
        }
        return bytes;
    }

    Generator[] getGenerators() {
        if (generators == null) {
            Metrics.measure("Item readGenerators bytes", () -> {
                try (final Input input = new Input(bytes)) {
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
                    this.generators = generators;
                }
            });
        }
        return generators;
    }

    @Override
    public String toString() {
        final Generator[] generators = getGenerators();
        return "[" +
                Arrays
                        .stream(generators)
                        .map(generator -> {
                            if (generator == null) {
                                return "null";
                            }
                            return generator.eval().toString();
                        })
                        .collect(Collectors.joining(", ")) +
                "]}";
    }
}
