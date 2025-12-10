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

package stroom.data.retention.api;

import stroom.data.retention.shared.DataRetentionRule;

import java.util.Comparator;
import java.util.Objects;

public class DataRetentionRuleAction {

    private final DataRetentionRule dataRetentionRule;
    private final RetentionRuleOutcome retentionRuleOutcome;

    public DataRetentionRuleAction(final DataRetentionRule dataRetentionRule,
                                   final RetentionRuleOutcome retentionRuleOutcome) {
        this.dataRetentionRule = Objects.requireNonNull(dataRetentionRule);
        this.retentionRuleOutcome = Objects.requireNonNull(retentionRuleOutcome);
    }

    public DataRetentionRule getRule() {
        return dataRetentionRule;
    }

    public RetentionRuleOutcome getOutcome() {
        return retentionRuleOutcome;
    }

    public static Comparator<DataRetentionRuleAction> comparingByRuleNo() {
        return Comparator.comparingInt(dataRetentionRuleAction ->
                dataRetentionRuleAction.getRule().getRuleNumber());
    }

    @Override
    public String toString() {
        return "DataRetentionRuleAction{" +
                "retentionRuleOutcome=" + retentionRuleOutcome +
                ", dataRetentionRule=" + dataRetentionRule +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataRetentionRuleAction that = (DataRetentionRuleAction) o;
        return retentionRuleOutcome == that.retentionRuleOutcome &&
                dataRetentionRule.equals(that.dataRetentionRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retentionRuleOutcome, dataRetentionRule);
    }
}
