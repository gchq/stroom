package stroom.query.common.v2;

import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;

import java.util.ArrayList;
import java.util.List;

class StoredValueKeyFactoryImpl implements StoredValueKeyFactory {

    private final CompiledDepths compiledDepths;
    private final Generator[][] groupGenerators;
    private final Generator timeGenerator;
    private final ValHasher valHasher;

    StoredValueKeyFactoryImpl(final CompiledDepths compiledDepths,
                              final CompiledField[] compiledFieldArray,
                              final KeyFactoryConfig keyFactoryConfig,
                              final ValHasher valHasher) {
        this.compiledDepths = compiledDepths;
        this.valHasher = valHasher;
        Generator[][] groupGenerators;
        Generator timeGenerator = null;

        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        groupGenerators = new Generator[groupSizeByDepth.length][];
        for (int depth = 0; depth < groupIndicesByDepth.length; depth++) {
            final boolean[] groupIndices = groupIndicesByDepth[depth];
            final List<Generator> list = new ArrayList<>(groupSizeByDepth[depth]);
            for (int i = 0; i < compiledFieldArray.length; i++) {
                final CompiledField compiledField = compiledFieldArray[i];
                final Generator generator = compiledField.getGenerator();
                // If we are grouping at this level then evaluate the expression and add to the group values.
                if (groupIndices[i]) {
                    list.add(generator);
                }

                // Get the value if this is a special field.
                if (i == keyFactoryConfig.getTimeFieldIndex()) {
                    timeGenerator = generator;
                }
            }
            groupGenerators[depth] = list.toArray(new Generator[0]);
        }

        this.groupGenerators = groupGenerators;
        this.timeGenerator = timeGenerator;
    }

    @Override
    public Val[] getGroupValues(final int depth, final StoredValues storedValues) {
        final boolean grouped = depth <= compiledDepths.getMaxGroupDepth();

        if (grouped) {
            final Generator[] generators = groupGenerators[depth];
            final Val[] values = new Val[generators.length];
            for (int i = 0; i < generators.length; i++) {
                values[i] = generators[i].eval(storedValues, null);
            }
            return values;
        }
        return Val.empty();
    }

    @Override
    public long getGroupHash(final int depth, final StoredValues storedValues) {
        final Val[] groupValues = getGroupValues(depth, storedValues);
        return hash(groupValues);
    }

    @Override
    public long hash(final Val[] groupValues) {
        return valHasher.hash(groupValues);
    }

    @Override
    public long getTimeMs(final StoredValues storedValues) {
        long timeMs = 0;
        if (timeGenerator != null) {
            final Long val = timeGenerator.eval(storedValues, null).toLong();
            if (val != null) {
                timeMs = val;
            }
        }
        return timeMs;
    }
}
