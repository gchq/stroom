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

package stroom.receive.rules.shared;

import stroom.task.shared.Action;

public class ReceiveDataRuleSetAction extends Action<ReceiveDataRuleSet> {
    private static final long serialVersionUID = 1966204124382806515L;

    private ReceiveDataRuleSet receiveDataRuleSet;

    public ReceiveDataRuleSetAction() {
        // Default constructor for GWT serialisation.
    }

    public ReceiveDataRuleSetAction(final ReceiveDataRuleSet receiveDataRuleSet) {
        this.receiveDataRuleSet = receiveDataRuleSet;
    }

    public ReceiveDataRuleSet getReceiveDataRuleSet() {
        return receiveDataRuleSet;
    }

    @Override
    public String getTaskName() {
        return "Save Receive Data Rule Set";
    }
}
