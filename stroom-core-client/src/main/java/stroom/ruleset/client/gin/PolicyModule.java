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

package stroom.ruleset.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.ruleset.client.DataReceiptPolicyPlugin;
import stroom.ruleset.client.DataRetentionPolicyPlugin;
import stroom.ruleset.client.presenter.DataReceiptPolicySettingsPresenter;
import stroom.ruleset.client.presenter.DataReceiptPolicySettingsPresenter.DataReceiptPolicySettingsView;
import stroom.ruleset.client.presenter.DataReceiptRulePresenter;
import stroom.ruleset.client.presenter.DataReceiptRulePresenter.DataReceiptRuleView;
import stroom.ruleset.client.presenter.DataRetentionPolicyPresenter;
import stroom.ruleset.client.presenter.DataRetentionPolicyPresenter.DataRetentionPolicyView;
import stroom.ruleset.client.presenter.DataRetentionRulePresenter;
import stroom.ruleset.client.presenter.DataRetentionRulePresenter.DataRetentionRuleView;
import stroom.ruleset.client.presenter.EditExpressionPresenter;
import stroom.ruleset.client.presenter.EditExpressionPresenter.EditExpressionView;
import stroom.ruleset.client.presenter.FieldEditPresenter;
import stroom.ruleset.client.presenter.FieldEditPresenter.FieldEditView;
import stroom.ruleset.client.view.DataReceiptPolicySettingsViewImpl;
import stroom.ruleset.client.view.DataReceiptRuleViewImpl;
import stroom.ruleset.client.view.DataRetentionPolicyViewImpl;
import stroom.ruleset.client.view.DataRetentionRuleViewImpl;
import stroom.ruleset.client.view.EditExpressionViewImpl;
import stroom.ruleset.client.view.FieldEditViewImpl;

public class PolicyModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(DataReceiptPolicyPlugin.class);
        bindPlugin(DataRetentionPolicyPlugin.class);
        bindPresenterWidget(EditExpressionPresenter.class, EditExpressionView.class, EditExpressionViewImpl.class);
        bindPresenterWidget(DataReceiptRulePresenter.class, DataReceiptRuleView.class, DataReceiptRuleViewImpl.class);
        bindPresenterWidget(DataReceiptPolicySettingsPresenter.class, DataReceiptPolicySettingsView.class, DataReceiptPolicySettingsViewImpl.class);
        bindPresenterWidget(FieldEditPresenter.class, FieldEditView.class, FieldEditViewImpl.class);
        bindPresenterWidget(DataRetentionRulePresenter.class, DataRetentionRuleView.class, DataRetentionRuleViewImpl.class);
        bindPresenterWidget(DataRetentionPolicyPresenter.class, DataRetentionPolicyView.class, DataRetentionPolicyViewImpl.class);
    }
}
