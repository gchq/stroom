package stroom.search.impl;

import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;
import stroom.ui.config.shared.UiConfig;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

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

    private Sizes extractValues(final String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Long::valueOf)
                        .collect(Collectors.toList()));
            } catch (final Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.unlimited();
    }
}
