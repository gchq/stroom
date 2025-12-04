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

package stroom.data.retention.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DataRetentionDeleteSummary {

    @JsonProperty
    private final String feed;
    @JsonProperty
    private final String type;
    @JsonProperty
    private final int ruleNumber;
    @JsonProperty
    private final String ruleName;
    @JsonProperty
    private final int count;

    @JsonCreator
    public DataRetentionDeleteSummary(@JsonProperty("feed") final String feed,
                                      @JsonProperty("type") final String type,
                                      @JsonProperty("ruleNumber") final int ruleNumber,
                                      @JsonProperty("ruleName") final String ruleName,
                                      @JsonProperty("count") final int count) {
        this.feed = Objects.requireNonNull(feed);
        this.type = Objects.requireNonNull(type);
        this.ruleNumber = ruleNumber;
        this.ruleName = Objects.requireNonNull(ruleName);
        this.count = count;
    }

    public String getFeed() {
        return feed;
    }

    public String getType() {
        return type;
    }

    public int getRuleNumber() {
        return ruleNumber;
    }

    public String getRuleName() {
        return ruleName;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataRetentionDeleteSummary that = (DataRetentionDeleteSummary) o;
        return ruleNumber == that.ruleNumber &&
                count == that.count &&
                feed.equals(that.feed) &&
                type.equals(that.type) &&
                ruleName.equals(that.ruleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feed, type, ruleNumber, ruleName, count);
    }
}
