package stroom.search.impl;

import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;
import stroom.ui.config.shared.UiConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

public class SizesProviderImpl implements SizesProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SizesProviderImpl.class);

    private final Provider<UiConfig> uiConfigProvider;

    @Inject
    public SizesProviderImpl(final Provider<UiConfig> uiConfigProvider) {
        this.uiConfigProvider = uiConfigProvider;
    }

    @Override
    public Sizes getDefaultMaxResultsSizes() {
        return extractValues(uiConfigProvider.get().getDefaultMaxResults());
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
