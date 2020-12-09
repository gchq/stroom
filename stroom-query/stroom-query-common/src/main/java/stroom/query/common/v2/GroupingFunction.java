package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class GroupingFunction implements Function<List<Item>, List<Item>> {
    private final GeneratorCombiner[] generatorCombiners;

    public GroupingFunction(final int depth, final int maxGroupDepth, final CompiledField[] compiledFields) {
        generatorCombiners = new GeneratorCombiner[compiledFields.length];
        for (int i = 0; i < compiledFields.length; i++) {
            final CompiledField compiledField = compiledFields[i];
            generatorCombiners[i] = createGeneratorCombiner(depth, compiledField.getGroupDepth(), maxGroupDepth);
        }
    }

    @Override
    public List<Item> apply(final List<Item> items) {
        // Group items in the list.
        final Map<RawKey, Item> groupingMap = new HashMap<>();
        for (final Item item : items) {
            final RawKey key = item.getGroupKey();
            groupingMap.compute(key, (k, v) -> {
                Item result = v;

                if (result == null) {
                    result = item;
                } else {
                    // Combine the new item into the original item.
                    final Generator[] existingGenerators = result.getGenerators();
                    final Generator[] newGenerators = item.getGenerators();
                    final Generator[] combinedGenerators = new Generator[existingGenerators.length];
                    for (int i = 0; i < generatorCombiners.length; i++) {
                        combinedGenerators[i] = generatorCombiners[i].apply(
                                existingGenerators[i],
                                newGenerators[i]);
                    }
                    result = new Item(item.getGroupKey(), combinedGenerators);
                }

                return result;
            });
        }
        return new ArrayList<>(groupingMap.values());
    }

    private GeneratorCombiner createGeneratorCombiner(final int depth,
                                                      final int groupDepth,
                                                      final int maxGroupDepth) {
//        if (depth < maxGroupDepth || groupDepth >= depth) {
        // This field is grouped.
        return (existingValue, addedValue) -> {
            if (existingValue != null && addedValue != null) {
                existingValue.merge(addedValue);
            } else if (addedValue != null) {
                return addedValue;
            }
            return existingValue;
        };
//        }
//
//        // This field is not grouped so output existing.
//        return (existingValue, addedValue) -> existingValue;
    }


//    Generator output = null;
//
//        if (maxDepth >= depth) {
//        if (existingValue != null && addedValue != null) {
//            existingValue.merge(addedValue);
//            output = existingValue;
//        } else if (groupDepth >= 0 && groupDepth <= depth) {
//            // This field is grouped so output existing as it must match the
//            // added value.
//            output = existingValue;
//        }
//    } else {
//        // This field is not grouped so output existing.
//        output = existingValue;
//    }
//
//        return output;

    private interface GeneratorCombiner extends BiFunction<Generator, Generator, Generator> {
    }
}