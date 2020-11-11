package stroom.search.impl;

import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;
import stroom.ui.config.shared.UiConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SizesProviderImpl implements SizesProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SizesProviderImpl.class);

    private final UiConfig uiConfig;
    private final SearchConfig searchConfig;

    @Inject
    public SizesProviderImpl(final UiConfig uiConfig,
                             final SearchConfig searchConfig) {
        this.uiConfig = uiConfig;
        this.searchConfig = searchConfig;
    }

    @Override
    public Sizes getDefaultMaxResultsSizes() {
        return extractValues(uiConfig.getDefaultMaxResults());
    }

    @Override
    public Sizes getStoreSizes() {
        return extractValues(searchConfig.getStoreSize());
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }
}
