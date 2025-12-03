/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
