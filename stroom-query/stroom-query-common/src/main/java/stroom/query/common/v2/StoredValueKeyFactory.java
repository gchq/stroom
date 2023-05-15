package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ref.StoredValues;

import java.util.ArrayList;
import java.util.List;

public class StoredValueKeyFactory {
    private final CompiledDepths compiledDepths;
    private final Generator[][] groupGenerators;
    private final Generator timeGenerator;

    public StoredValueKeyFactory(final CompiledDepths compiledDepths,
                       final CompiledField[] compiledFieldArray,
                       final KeyFactoryConfig keyFactoryConfig) {
        this.compiledDepths = compiledDepths;
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

    public Key createKey(final Key parentKey, final long timeMs, final Val[] groupValues) {
        final int depth = parentKey.getDepth() + 1;
        final boolean grouped = depth <= compiledDepths.getMaxGroupDepth();

        if (grouped) {
            return parentKey.resolve(timeMs, groupValues);

        } else {
            // This item will not be grouped.
//            final long uniqueId = keyFactory.getUniqueId();
            return parentKey.resolve(timeMs, 0);
        }
    }

    public Key createKey(final Key parentKey, final StoredValues storedValues) {
        final int depth = parentKey.getDepth() + 1;
        final boolean grouped = depth <= compiledDepths.getMaxGroupDepth();

        if (grouped) {
            return parentKey.resolve(getTimeMs(storedValues), getGroupValues(parentKey.getDepth() + 1, storedValues));

        } else {
            // This item will not be grouped.
//            final long uniqueId = keyFactory.getUniqueId();
            return parentKey.resolve(getTimeMs(storedValues), 0);
        }
    }
}
