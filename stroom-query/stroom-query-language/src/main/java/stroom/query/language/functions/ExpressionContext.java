/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.HoppingWindow;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

public class ExpressionContext {

    private final int maxStringLength;
    private final DateTimeSettings dateTimeSettings;
    private final StateProvider stateProvider;
    private final AiProvider aiProvider;
    private final HoppingWindow window;

    public ExpressionContext() {
        this.maxStringLength = 100;
        this.dateTimeSettings = DateTimeSettings.builder().build();
        this.stateProvider = (map, key, effectiveTimeMs) -> ValNull.INSTANCE;
        this.aiProvider = (modelNameOrUuid, systemPrompt, message) -> ValNull.INSTANCE;
        this.window = null;
    }

    @JsonCreator
    public ExpressionContext(final Integer maxStringLength,
                             final DateTimeSettings dateTimeSettings,
                             final StateProvider stateProvider,
                             final AiProvider aiProvider) {
        this(maxStringLength, dateTimeSettings, stateProvider, aiProvider, null);
    }

    private ExpressionContext(final Integer maxStringLength,
                              final DateTimeSettings dateTimeSettings,
                              final StateProvider stateProvider,
                              final AiProvider aiProvider,
                              final HoppingWindow window) {
        this.maxStringLength = Objects.requireNonNullElse(maxStringLength, 0);
        this.dateTimeSettings = dateTimeSettings;
        this.stateProvider = stateProvider;
        this.aiProvider = aiProvider;
        this.window = window;
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    public StateProvider getStateProvider() {
        return stateProvider;
    }

    public AiProvider getAiProvider() {
        return aiProvider;
    }

    /// Gets the time windows of the query being parsed, which the window functions written in it,
    /// such as `windowStart()`, work with.
    ///
    /// @return The windows of the query's `window` clause, or `null` if the query has none or is
    ///         not being parsed from StroomQL.
    public HoppingWindow getWindow() {
        return window;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExpressionContext that = (ExpressionContext) o;
        return maxStringLength == that.maxStringLength &&
                Objects.equals(dateTimeSettings, that.dateTimeSettings) &&
                Objects.equals(window, that.window);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxStringLength, dateTimeSettings, window);
    }

    @Override
    public String toString() {
        return "ExpressionContext{" +
                "maxStringLength=" + maxStringLength +
                ", dateTimeSettings=" + dateTimeSettings +
                ", window=" + window +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private int maxStringLength;
        private DateTimeSettings dateTimeSettings;
        private StateProvider stateProvider;
        private AiProvider aiProvider;
        private HoppingWindow window;

        private Builder() {
        }

        private Builder(final ExpressionContext expressionContext) {
            this.maxStringLength = expressionContext.maxStringLength;
            this.dateTimeSettings = expressionContext.dateTimeSettings;
            this.stateProvider = expressionContext.stateProvider;
            this.aiProvider = expressionContext.aiProvider;
            this.window = expressionContext.window;
        }

        public Builder maxStringLength(final int maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        public Builder dateTimeSettings(final DateTimeSettings dateTimeSettings) {
            this.dateTimeSettings = dateTimeSettings;
            return this;
        }

        public Builder stateProvider(final StateProvider stateProvider) {
            this.stateProvider = stateProvider;
            return this;
        }

        public Builder aiProvider(final AiProvider aiProvider) {
            this.aiProvider = aiProvider;
            return this;
        }

        /// Sets the time windows of the query being parsed.
        ///
        /// @param window The windows of the query's `window` clause, or `null` for none.
        /// @return This builder.
        public Builder window(final HoppingWindow window) {
            this.window = window;
            return this;
        }

        public ExpressionContext build() {
            return new ExpressionContext(maxStringLength, dateTimeSettings, stateProvider, aiProvider, window);
        }
    }
}
