package stroom.util.io.capacity;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HasCapacitySelectorFactory {

    public static final String DEFAULT_SELECTOR_NAME = new RoundRobinCapacitySelector().getName();

    // Can't be built dynamically as it is used in annotations
    // Must be kept in sync with the map below.
    public static final String VOLUME_SELECTOR_PATTERN = "^(" +
            RoundRobinCapacitySelector.NAME + "|" +
            MostFreePercentCapacitySelector.NAME + "|" +
            MostFreeCapacitySelector.NAME + "|" +
            RandomCapacitySelector.NAME + "|" +
            RoundRobinIgnoreLeastFreePercentCapacitySelector.NAME + "|" +
            RoundRobinIgnoreLeastFreeCapacitySelector.NAME + "|" +
            RoundRobinCapacitySelector.NAME + "|" +
            WeightedFreePercentRandomCapacitySelector.NAME + "|" +
            WeightedFreeRandomCapacitySelector.NAME + ")$";

    // Need to create an instance briefly just to get the name
    private static final Map<String, Supplier<HasCapacitySelector>> SELECTOR_FACTORY_MAP = Stream.of(
                    new MostFreePercentCapacitySelector(),
                    new MostFreeCapacitySelector(),
                    new RandomCapacitySelector(),
                    new RoundRobinIgnoreLeastFreePercentCapacitySelector(),
                    new RoundRobinIgnoreLeastFreeCapacitySelector(),
                    new RoundRobinCapacitySelector(),
                    new WeightedFreePercentRandomCapacitySelector(),
                    new WeightedFreeRandomCapacitySelector()
            )
            .collect(Collectors.toMap(
                    HasCapacitySelector::getName,
                    HasCapacitySelectorFactory::createSelectorSupplier
            ));

    public HasCapacitySelectorFactory() {
        if (!SELECTOR_FACTORY_MAP.containsKey(DEFAULT_SELECTOR_NAME)) {
            throw new RuntimeException("Selector " + DEFAULT_SELECTOR_NAME + " not found in map");
        }
    }

    public Optional<HasCapacitySelector> createSelector(final String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        } else {
            final Supplier<HasCapacitySelector> supplier = SELECTOR_FACTORY_MAP.get(name);
            if (supplier == null) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(supplier.get());
            }
        }
    }

    public HasCapacitySelector createSelectorOrDefault(final String name) {
        return createSelector(name)
                .orElseGet(this::createDefaultSelector);
    }

    public HasCapacitySelector createDefaultSelector() {
        return createSelector(DEFAULT_SELECTOR_NAME)
                .orElseThrow(() -> new RuntimeException(
                        "Default selector " + DEFAULT_SELECTOR_NAME + " not found"));
    }

    public static Set<String> getSelectorNames() {
        return SELECTOR_FACTORY_MAP.keySet();
    }

    private static Supplier<HasCapacitySelector> createSelectorSupplier(final HasCapacitySelector selector) {
        try {
            final var constructor = selector.getClass()
                    .getConstructor();

            return () -> {
                try {
                    return constructor.newInstance();
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException e) {
                    throw new RuntimeException("Unable to instantiate "
                            + selector.getClass().getName()
                            + ". Is there a public no-args constructor?");
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Unable to instantiate "
                    + selector.getClass().getName()
                    + ". Is there a public no-args constructor?");
        }
    }
}
