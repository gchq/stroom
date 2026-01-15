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

package stroom.meta.api;

import stroom.util.Period;

import java.util.Objects;

/**
 * Class used as the search criteria for effective type searches.
 * These are from date range based searches.
 */
public class EffectiveMetaDataCriteria {

    private Period effectivePeriod;
    private String feed;
    private String type;

    public EffectiveMetaDataCriteria(final Period effectivePeriod, final String feed, final String type) {
        this.effectivePeriod = Objects.requireNonNull(effectivePeriod);
        this.feed = Objects.requireNonNull(feed);
        this.type = Objects.requireNonNull(type);
    }

    public String getFeed() {
        return feed;
    }

    public void setFeed(final String feed) {
        this.feed = feed;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public Period getEffectivePeriod() {
        return effectivePeriod;
    }

    public void setEffectivePeriod(final Period effectivePeriod) {
        this.effectivePeriod = effectivePeriod;
    }

    @Override
    public String toString() {
        return "EffectiveMetaDataCriteria{" +
                "effectivePeriod=" + effectivePeriod +
                ", feed='" + feed + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
