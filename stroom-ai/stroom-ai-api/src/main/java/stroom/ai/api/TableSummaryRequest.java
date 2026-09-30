/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.api;

import stroom.ai.shared.TableAnalysisConfig;
import stroom.docref.DocRef;

import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * What to summarise, which model to ask, and how much of it to do at once.
 *
 * @see TableSummariser
 */
public class TableSummaryRequest {

    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    private final List<TableSource> sources;
    private final ChatModel chatModel;
    private final DocRef modelRef;
    private final TableAnalysisConfig config;
    private final String query;
    private final String context;
    private final TableSummaryProgressListener progressListener;
    private final BooleanSupplier cancelled;

    private TableSummaryRequest(final List<TableSource> sources,
                                final ChatModel chatModel,
                                final DocRef modelRef,
                                final TableAnalysisConfig config,
                                final String query,
                                final String context,
                                final TableSummaryProgressListener progressListener,
                                final BooleanSupplier cancelled) {
        this.sources = Objects.requireNonNull(sources, "No sources supplied");
        if (chatModel == null && modelRef == null) {
            throw new IllegalArgumentException("No model supplied - set either a chat model or a model ref");
        }
        if (chatModel != null && modelRef != null) {
            throw new IllegalArgumentException("Set either a chat model or a model ref, not both");
        }
        this.chatModel = chatModel;
        this.modelRef = modelRef;
        this.config = Objects.requireNonNull(config, "No table analysis config supplied");
        this.query = Objects.requireNonNull(query, "No query supplied");
        this.context = context;
        this.progressListener = Objects.requireNonNullElse(progressListener, TableSummaryProgressListener.NO_OP);
        this.cancelled = Objects.requireNonNullElse(cancelled, NEVER_CANCELLED);
    }

    public List<TableSource> getSources() {
        return sources;
    }

    /**
     * @return The model to ask, already built, or null if {@link #getModelRef()} says which one to build.
     */
    public ChatModel getChatModel() {
        return chatModel;
    }

    /**
     * @return Which model to ask, for a caller that has no reason to build one itself, or null if
     * {@link #getChatModel()} supplies one. Resolving it also checks the caller's permission to use it.
     */
    public DocRef getModelRef() {
        return modelRef;
    }

    public TableAnalysisConfig getConfig() {
        return config;
    }

    /**
     * @return What to ask of the data, e.g. the user's question or the report's summary prompt.
     */
    public String getQuery() {
        return query;
    }

    /**
     * @return Anything the model should know beyond the data and the query, e.g. the conversation so far.
     * May be null.
     */
    public String getContext() {
        return context;
    }

    public TableSummaryProgressListener getProgressListener() {
        return progressListener;
    }

    /**
     * @return Asked repeatedly as the summary proceeds. Never null; defaults to never cancelled.
     */
    public BooleanSupplier getCancelled() {
        return cancelled;
    }

    @Override
    public String toString() {
        return "TableSummaryRequest{" +
               "sources=" + sources.size() +
               ", modelRef=" + modelRef +
               ", queryLength=" + query.length() +
               ", config=" + config +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private List<TableSource> sources;
        private ChatModel chatModel;
        private DocRef modelRef;
        private TableAnalysisConfig config;
        private String query;
        private String context;
        private TableSummaryProgressListener progressListener;
        private BooleanSupplier cancelled;

        private Builder() {
        }

        public Builder sources(final List<TableSource> sources) {
            this.sources = sources;
            return this;
        }

        public Builder source(final TableSource source) {
            this.sources = List.of(source);
            return this;
        }

        /**
         * The model to ask. Use this where you have already built one; otherwise use {@link #modelRef}.
         */
        public Builder chatModel(final ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /**
         * Which model to ask, leaving it to be read and built for you. Use this or {@link #chatModel}.
         */
        public Builder modelRef(final DocRef modelRef) {
            this.modelRef = modelRef;
            return this;
        }

        public Builder config(final TableAnalysisConfig config) {
            this.config = config;
            return this;
        }

        public Builder query(final String query) {
            this.query = query;
            return this;
        }

        public Builder context(final String context) {
            this.context = context;
            return this;
        }

        public Builder progressListener(final TableSummaryProgressListener progressListener) {
            this.progressListener = progressListener;
            return this;
        }

        public Builder cancelled(final BooleanSupplier cancelled) {
            this.cancelled = cancelled;
            return this;
        }

        public TableSummaryRequest build() {
            return new TableSummaryRequest(
                    sources, chatModel, modelRef, config, query, context, progressListener, cancelled);
        }
    }
}
