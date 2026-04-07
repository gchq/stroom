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

package stroom.dashboard.shared;

import stroom.query.api.TableSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractQueryComponentSettings implements HasSelectionQuery {

    @JsonProperty
    private final Automate automate;
    @JsonProperty
    private final List<ComponentSelectionHandler> selectionHandlers;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public AbstractQueryComponentSettings(@JsonProperty("automate") final Automate automate,
                                          @JsonProperty("selectionHandlers") final List<ComponentSelectionHandler> selectionHandlers) {
        this.automate = automate;
        this.selectionHandlers = selectionHandlers;
    }

    public Automate getAutomate() {
        return automate;
    }

    @JsonIgnore
    @Override
    public List<ComponentSelectionHandler> getSelectionQuery() {
        return selectionHandlers;
    }

    @Deprecated
    public List<ComponentSelectionHandler> getSelectionHandlers() {
        return selectionHandlers;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractQueryComponentSettings that = (AbstractQueryComponentSettings) o;

//        // TODO : REMOVE - GWT DEBUG
//        final boolean b1 = Objects.equals(automate, that.automate);
//        final boolean b2 = Objects.equals(selectionHandlers, that.selectionHandlers);

        return Objects.equals(automate, that.automate) &&
               Objects.equals(selectionHandlers, that.selectionHandlers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(automate, selectionHandlers);
    }

    @Override
    public String toString() {
        return "AbstractQueryComponentSettings{" +
               "automate=" + automate +
               ", selectionHandlers=" + selectionHandlers +
               '}';
    }

    /**
     * Builder for constructing a {@link TableSettings tableSettings}
     */
    public abstract static class AbstractBuilder
            <T extends ComponentSettings, B extends ComponentSettings.AbstractBuilder<T, ?>>
            extends ComponentSettings.AbstractBuilder<T, B>
            implements HasSelectionQueryBuilder<T, B> {

        Automate automate;
        List<ComponentSelectionHandler> selectionQuery;

        AbstractBuilder() {
        }

        AbstractBuilder(final AbstractQueryComponentSettings settings) {
            this.automate = settings.automate == null
                    ? null
                    : settings.automate.copy().build();
            this.selectionQuery = settings.selectionHandlers == null
                    ? null
                    : new ArrayList<>(settings.selectionHandlers);
        }

        public B automate(final Automate automate) {
            this.automate = automate;
            return self();
        }

        @Override
        public B selectionQuery(final List<ComponentSelectionHandler> selectionQuery) {
            this.selectionQuery = selectionQuery;
            return self();
        }
    }
}
