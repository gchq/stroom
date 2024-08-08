/*
 * Copyright 2024 Crown Copyright
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
import stroom.util.shared.string.CIKey;

import java.io.Serializable;
import java.util.List;

/**
 * This class represents a statistic to be gathered by Stroom.
 */
class MetaStatisticsTemplate implements Serializable {

    private static final long serialVersionUID = -2347332113575225973L;

    private InternalStatisticKey key;
    private CIKey timeMsAttribute;
    private CIKey incrementAttribute;
    private List<CIKey> tagAttributeList;

    MetaStatisticsTemplate() {
    }

    MetaStatisticsTemplate(final InternalStatisticKey key,
                           final CIKey timeMsAttribute,
                           final List<CIKey> tagAttributeList) {
        this(key, timeMsAttribute, null, tagAttributeList);
    }

    MetaStatisticsTemplate(final InternalStatisticKey key,
                           final CIKey timeMsAttribute,
                           final CIKey incrementAttribute,
                           final List<CIKey> tagAttributeList) {
        this.key = key;
        this.timeMsAttribute = timeMsAttribute;
        this.incrementAttribute = incrementAttribute;
        this.tagAttributeList = tagAttributeList;
    }

    public CIKey getTimeMsAttribute() {
        return timeMsAttribute;
    }

    public void setTimeMsAttribute(final CIKey timeMsAttribute) {
        this.timeMsAttribute = timeMsAttribute;
    }

    public List<CIKey> getTagAttributeList() {
        return tagAttributeList;
    }

    public void setTagAttributeList(final List<CIKey> tagAttributeList) {
        this.tagAttributeList = tagAttributeList;
    }

    public InternalStatisticKey getKey() {
        return key;
    }

    public void setKey(final InternalStatisticKey key) {
        this.key = key;
    }

    public CIKey getIncrementAttribute() {
        return incrementAttribute;
    }

    public void setIncrementAttribute(final CIKey incrementAttribute) {
        this.incrementAttribute = incrementAttribute;
    }

    @Override
    public String toString() {
        return "MetaStatisticsTemplate{" +
                "key=" + key +
                ", timeMsAttribute=" + timeMsAttribute +
                ", incrementAttribute=" + incrementAttribute +
                ", tagAttributeList=" + tagAttributeList +
                '}';
    }
}
