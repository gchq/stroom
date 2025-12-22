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

package stroom.meta.statistics.impl;

import stroom.statistics.api.InternalStatisticKey;

import java.io.Serializable;
import java.util.List;

/**
 * This class represents a statistic to be gathered by Stroom.
 */
class MetaStatisticsTemplate implements Serializable {

    private static final long serialVersionUID = -2347332113575225973L;

    private InternalStatisticKey key;
    private String timeMsAttribute;
    private String incrementAttribute;
    private List<String> tagAttributeList;

    MetaStatisticsTemplate() {
    }

    MetaStatisticsTemplate(final InternalStatisticKey key, final String timeMsAttribute,
                           final List<String> tagAttributeList) {
        this(key, timeMsAttribute, null, tagAttributeList);
    }

    MetaStatisticsTemplate(final InternalStatisticKey key,
                           final String timeMsAttribute,
                           final String incrementAttribute,
                           final List<String> tagAttributeList) {
        this.key = key;
        this.timeMsAttribute = timeMsAttribute;
        this.incrementAttribute = incrementAttribute;
        this.tagAttributeList = tagAttributeList;
    }

    public String getTimeMsAttribute() {
        return timeMsAttribute;
    }

    public void setTimeMsAttribute(final String timeMsAttribute) {
        this.timeMsAttribute = timeMsAttribute;
    }

    public List<String> getTagAttributeList() {
        return tagAttributeList;
    }

    public void setTagAttributeList(final List<String> tagAttributeList) {
        this.tagAttributeList = tagAttributeList;
    }

    public InternalStatisticKey getKey() {
        return key;
    }

    public void setKey(final InternalStatisticKey key) {
        this.key = key;
    }

    public String getIncrementAttribute() {
        return incrementAttribute;
    }

    public void setIncrementAttribute(final String incrementAttribute) {
        this.incrementAttribute = incrementAttribute;
    }
}
