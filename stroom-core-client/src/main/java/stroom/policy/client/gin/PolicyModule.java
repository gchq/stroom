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

package stroom.policy.client.gin;

import stroom.app.client.gin.PluginModule;
import stroom.policy.client.DataRetentionPolicyPlugin;
import stroom.policy.client.presenter.DataReceiptPolicySettingsPresenter;
import stroom.policy.client.presenter.DataReceiptPolicySettingsPresenter.DataReceiptPolicySettingsView;
import stroom.policy.client.presenter.DataReceiptRulePresenter;
import stroom.policy.client.presenter.DataReceiptRulePresenter.DataReceiptRuleView;
import stroom.policy.client.presenter.DataRetentionPolicyPresenter;
import stroom.policy.client.presenter.DataRetentionPolicyPresenter.DataRetentionPolicyView;
import stroom.policy.client.presenter.DataRetentionRulePresenter;
import stroom.policy.client.presenter.DataRetentionRulePresenter.DataRetentionRuleView;
import stroom.policy.client.presenter.EditExpressionPresenter;
import stroom.policy.client.presenter.EditExpressionPresenter.EditExpressionView;
import stroom.policy.client.presenter.FieldEditPresenter;
import stroom.policy.client.presenter.FieldEditPresenter.FieldEditView;
import stroom.policy.client.view.DataReceiptPolicySettingsViewImpl;
import stroom.policy.client.view.DataReceiptRuleViewImpl;
import stroom.policy.client.view.DataRetentionPolicyViewImpl;
import stroom.policy.client.view.DataRetentionRuleViewImpl;
import stroom.policy.client.view.EditExpressionViewImpl;
import stroom.policy.client.view.FieldEditViewImpl;

public class PolicyModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(DataRetentionPolicyPlugin.class);
        bindPresenterWidget(EditExpressionPresenter.class, EditExpressionView.class, EditExpressionViewImpl.class);
        bindPresenterWidget(DataReceiptRulePresenter.class, DataReceiptRuleView.class, DataReceiptRuleViewImpl.class);
        bindPresenterWidget(DataReceiptPolicySettingsPresenter.class, DataReceiptPolicySettingsView.class, DataReceiptPolicySettingsViewImpl.class);
        bindPresenterWidget(FieldEditPresenter.class, FieldEditView.class, FieldEditViewImpl.class);
        bindPresenterWidget(DataRetentionRulePresenter.class, DataRetentionRuleView.class, DataRetentionRuleViewImpl.class);
        bindPresenterWidget(DataRetentionPolicyPresenter.class, DataRetentionPolicyView.class, DataRetentionPolicyViewImpl.class);
    }
}
