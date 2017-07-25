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

package stroom.entity.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to get summary data from services
 * <p>
 * Heading1 Heading2 Heading3 .... RowLabel Count1 Count2 ....
 */
public class SummaryDataRow implements SharedObject {
    private static final long serialVersionUID = 5631193345714122209L;

    private List<String> label = new ArrayList<>();
    private List<Long> key = new ArrayList<>();
    private Long count = null;

    public List<String> getLabel() {
        return label;
    }

    public void setLabel(List<String> label) {
        this.label = label;
    }

    public List<Long> getKey() {
        return key;
    }

    public void setKey(List<Long> key) {
        this.key = key;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(label);
        builder.append(key);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof SummaryDataRow)) {
            return false;
        }

        final SummaryDataRow sdr = (SummaryDataRow) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.label, sdr.label);
        builder.append(this.key, sdr.key);

        return builder.isEquals();
    }
}
