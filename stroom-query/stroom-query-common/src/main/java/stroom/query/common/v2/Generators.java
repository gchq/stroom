package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.Generator;
import stroom.util.logging.Metrics;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Generators {

    private final Serialisers serialisers;
    private final CompiledField[] fields;
    private byte[] bytes;
    private Generator[] generators;

    public Generators(final Serialisers serialisers,
                      final CompiledField[] fields,
                      final Generator[] generators) {
        this.serialisers = serialisers;
        this.fields = fields;
        this.generators = generators;
    }

    public Generators(final Serialisers serialisers,
                      final CompiledField[] fields,
                      final byte[] bytes) {
        this.serialisers = serialisers;
        this.fields = fields;
        this.bytes = bytes;
    }

    byte[] getBytes(final ErrorConsumer errorConsumer) {
        if (bytes == null) {
            Metrics.measure("Item toBytes", () -> {
                try (final Output output = serialisers.getOutputFactory().createValueOutput(errorConsumer)) {
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
                try (final Input input = serialisers.getInputFactory().create(bytes)) {
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
                            return generator.eval(null).toString();
                        })
                        .collect(Collectors.joining(", ")) +
                "]}";
    }
}
