/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.sql;

import stroom.statistics.shared.StatisticType;

import java.io.Serializable;

public class SQLStatisticValueSourceDO implements Serializable {
    private static final long serialVersionUID = -4956231944878929284L;

    private long createMs;
    private String name;
    private long value;
    private StatisticType type;

    public long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final long createMs) {
        this.createMs = createMs;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public long getValue() {
        return value;
    }

    public void setValue(final long value) {
        this.value = value;
    }

    public StatisticType getType() {
        return type;
    }

    public void setType(final StatisticType type) {
        this.type = type;
    }
}
