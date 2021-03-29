package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

public class GroupingFunction implements Function<Stream<UnpackedItem>, Stream<UnpackedItem>> {

    //    private final GeneratorCombiner[] generatorCombiners;
    private final ItemSerialiser itemSerialiser;

//    public GroupingFunction(final int depth, final int maxGroupDepth, final CompiledField[] compiledFields) {
//        generatorCombiners = new GeneratorCombiner[compiledFields.length];
//        for (int i = 0; i < compiledFields.length; i++) {
//            final CompiledField compiledField = compiledFields[i];
//            generatorCombiners[i] = createGeneratorCombiner(depth, compiledField.getGroupDepth(), maxGroupDepth);
//        }
//    }


    public GroupingFunction(final ItemSerialiser itemSerialiser) {
        this.itemSerialiser = itemSerialiser;
    }

    @Override
    public Stream<UnpackedItem> apply(final Stream<UnpackedItem> stream) {
//        // Group items in the list.
//        final Map<RawKey, Item> groupingMap = new HashMap<>();
//        for (final Item item : items) {
//            final RawKey key = item.getRawKey();
//            groupingMap.compute(key, (k, v) -> {
//                Item result = v;
//
//                if (result == null) {
//                    result = item;
//                } else {
//
//
//                    // Combine the new item into the original item.
//                    final Generator[] existingGenerators = ((ItemImpl)result).getGenerators();
//                    final Generator[] newGenerators = ((ItemImpl)item).getGenerators();
//                    final Generator[] combinedGenerators = new Generator[existingGenerators.length];
//                    for (int i = 0; i < generatorCombiners.length; i++) {
//                        combinedGenerators[i] = generatorCombiners[i].apply(
//                                existingGenerators[i],
//                                newGenerators[i]);
//                    }
//                    result = new ItemImpl(item.getGroupKey(), combinedGenerators);
//                }
//
//                return result;
//            });
//        }
//        return new ArrayList<>(groupingMap.values());

//        return () -> {
        final Map<RawKey, Generator[]> groupingMap = new ConcurrentHashMap<>();
        stream.forEach(unpackedItem -> {
            final RawKey rawKey = unpackedItem.getRawKey();
            final Generator[] generators = unpackedItem.getGenerators();

            groupingMap.compute(rawKey, (k, v) -> {
                Generator[] result = v;

                if (result == null) {
                    result = generators;
                } else {
                    // Combine the new item into the original item.
                    for (int i = 0; i < result.length; i++) {
                        Generator existingGenerator = result[i];
                        Generator newGenerator = generators[i];
                        if (newGenerator != null) {
                            if (existingGenerator == null) {
                                result[i] = newGenerator;
                            } else {
                                existingGenerator.merge(newGenerator);
                            }
                        }
                    }
                }

                return result;
            });
        });
        return groupingMap
                .entrySet()
                .parallelStream()
                .map(e -> {
                    final RawItem rawItem = new RawItem(e.getKey().getBytes(), itemSerialiser.toBytes(e.getValue()));
                    return new UnpackedItem(e.getKey(), e.getValue(), itemSerialiser.toBytes(rawItem));
                });
//        };
    }

//    private GeneratorCombiner createGeneratorCombiner(final int depth,
//                                                      final int groupDepth,
//                                                      final int maxGroupDepth) {
////        if (depth < maxGroupDepth || groupDepth >= depth) {
//        // This field is grouped.
//        return (existingValue, addedValue) -> {
//            if (existingValue != null && addedValue != null) {
//                existingValue.merge(addedValue);
//            } else if (addedValue != null) {
//                return addedValue;
//            }
//            return existingValue;
//        };
////        }
////
////        // This field is not grouped so output existing.
////        return (existingValue, addedValue) -> existingValue;
//    }


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
//
//    private interface GeneratorCombiner extends BiFunction<Generator, Generator, Generator> {
//    }
}
