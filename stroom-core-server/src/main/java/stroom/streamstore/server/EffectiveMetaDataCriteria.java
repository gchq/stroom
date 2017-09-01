/*
 * Copyright 2017 Crown Copyright
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

package stroom.streamstore.server;

import stroom.entity.shared.Period;
import stroom.query.api.v2.DocRef;

import java.io.Serializable;

/**
 * <p>
 * Class used as the search criteria for effective type searches.
 * </p>
 * <p>
 * <p>
 * These are from date range based searches.
 * </p>
 */
public class EffectiveMetaDataCriteria implements Serializable {
    private static final long serialVersionUID = 1L;

    private Period effectivePeriod;
    private DocRef feed;
    private String streamType;

    public DocRef getFeed() {
        return feed;
    }

    public void setFeed(final DocRef feed) {
        this.feed = feed;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    public Period getEffectivePeriod() {
        return effectivePeriod;
    }

    public void setEffectivePeriod(final Period effectivePeriod) {
        this.effectivePeriod = effectivePeriod;
    }
}
