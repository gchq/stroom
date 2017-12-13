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
 *
 */

package stroom.ruleset.shared;

import stroom.entity.shared.Action;

public class UpdateDataReceiptPolicyAction extends Action<RuleSet> {
    private static final long serialVersionUID = 1966204124382806515L;

    private RuleSet dataReceiptPolicy;

    public UpdateDataReceiptPolicyAction() {
        // Default constructor for GWT serialisation.
    }

    public UpdateDataReceiptPolicyAction(final RuleSet dataReceiptPolicy) {
        this.dataReceiptPolicy = dataReceiptPolicy;
    }

    public RuleSet getDataReceiptPolicy() {
        return dataReceiptPolicy;
    }

    @Override
    public String getTaskName() {
        return "Save Data Receipt Policy";
    }
}
