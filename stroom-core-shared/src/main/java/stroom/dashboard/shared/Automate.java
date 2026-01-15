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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"open", "refresh", "refreshInterval"})
@JsonInclude(Include.NON_NULL)
public class Automate {

    @JsonProperty("open")
    private final boolean open;
    @JsonProperty("refresh")
    private final boolean refresh;
    @JsonProperty("refreshInterval")
    private final String refreshInterval;

    @JsonCreator
    public Automate(@JsonProperty("open") final boolean open,
                    @JsonProperty("refresh") final boolean refresh,
                    @JsonProperty("refreshInterval") final String refreshInterval) {
        this.open = open;
        this.refresh = refresh;
        this.refreshInterval = refreshInterval;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isRefresh() {
        return refresh;
    }

    public String getRefreshInterval() {
        return refreshInterval;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Automate automate = (Automate) o;
        return open == automate.open &&
                refresh == automate.refresh &&
                Objects.equals(refreshInterval, automate.refreshInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(open, refresh, refreshInterval);
    }

    @Override
    public String toString() {
        return "Automate{" +
                "open=" + open +
                ", refresh=" + refresh +
                ", refreshInterval='" + refreshInterval + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private boolean open;
        private boolean refresh;
        private String refreshInterval = "10s";

        private Builder() {
        }

        private Builder(final Automate automate) {
            this.open = automate.open;
            this.refresh = automate.refresh;
            this.refreshInterval = automate.refreshInterval;
        }

        public Builder open(final boolean open) {
            this.open = open;
            return this;
        }

        public Builder refresh(final boolean refresh) {
            this.refresh = refresh;
            return this;
        }

        public Builder refreshInterval(final String refreshInterval) {
            this.refreshInterval = refreshInterval;
            return this;
        }

        public Automate build() {
            return new Automate(open, refresh, refreshInterval);
        }
    }
}
