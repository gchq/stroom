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

package stroom.streamstore.shared;

import stroom.entity.shared.DocRef;
import stroom.query.shared.ExpressionTerm.Condition;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.io.Serializable;

public class StreamAttributeCondition implements Serializable {
    private static final long serialVersionUID = -2063409357774838870L;

    private DocRef streamAttributeKey;
    private Condition condition;
    private String fieldValue;

    public StreamAttributeCondition() {
        // Default constructor necessary for GWT serialisation.
    }

    public StreamAttributeCondition(final DocRef streamAttributeKey, final Condition condition,
            final String fieldValue) {
        this.streamAttributeKey = streamAttributeKey;
        this.condition = condition;
        this.fieldValue = fieldValue;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(final Condition condition) {
        this.condition = condition;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(final String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public DocRef getStreamAttributeKey() {
        return streamAttributeKey;
    }

    public void setStreamAttributeKey(final DocRef streamAttributeKey) {
        this.streamAttributeKey = streamAttributeKey;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(streamAttributeKey);
        builder.append(condition);
        builder.append(fieldValue);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof StreamAttributeCondition)) {
            return false;
        }

        final StreamAttributeCondition streamAttributeCondition = (StreamAttributeCondition) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(streamAttributeKey, streamAttributeCondition.streamAttributeKey);
        builder.append(condition, streamAttributeCondition.condition);
        builder.append(fieldValue, streamAttributeCondition.fieldValue);

        return builder.isEquals();
    }
}
