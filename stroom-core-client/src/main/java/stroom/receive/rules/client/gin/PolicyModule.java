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

package stroom.receive.rules.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.data.client.presenter.EditExpressionPresenter.EditExpressionView;
import stroom.data.client.view.EditExpressionViewImpl;
import stroom.receive.rules.client.DataRetentionPlugin;
import stroom.receive.rules.client.ReceiveDataRuleSetPlugin;
import stroom.receive.rules.client.presenter.DataRetentionPolicyPresenter;
import stroom.receive.rules.client.presenter.DataRetentionPolicyPresenter.DataRetentionPolicyView;
import stroom.receive.rules.client.presenter.DataRetentionPresenter;
import stroom.receive.rules.client.presenter.DataRetentionPresenter.DataRetentionView;
import stroom.receive.rules.client.presenter.DataRetentionRulePresenter;
import stroom.receive.rules.client.presenter.DataRetentionRulePresenter.DataRetentionRuleView;
import stroom.receive.rules.client.presenter.FieldEditPresenter;
import stroom.receive.rules.client.presenter.FieldEditPresenter.FieldEditView;
import stroom.receive.rules.client.presenter.RulePresenter;
import stroom.receive.rules.client.presenter.RulePresenter.RuleView;
import stroom.receive.rules.client.presenter.RuleSetSettingsPresenter;
import stroom.receive.rules.client.presenter.RuleSetSettingsPresenter.RuleSetSettingsView;
import stroom.receive.rules.client.view.DataRetentionPolicyViewImpl;
import stroom.receive.rules.client.view.DataRetentionRuleViewImpl;
import stroom.receive.rules.client.view.DataRetentionViewImpl;
import stroom.receive.rules.client.view.FieldEditViewImpl;
import stroom.receive.rules.client.view.RuleSetSettingsViewImpl;
import stroom.receive.rules.client.view.RuleViewImpl;

public class PolicyModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(ReceiveDataRuleSetPlugin.class);
        bindPlugin(DataRetentionPlugin.class);
        bindPresenterWidget(EditExpressionPresenter.class, EditExpressionView.class, EditExpressionViewImpl.class);
        bindPresenterWidget(RulePresenter.class, RuleView.class, RuleViewImpl.class);
        bindPresenterWidget(RuleSetSettingsPresenter.class, RuleSetSettingsView.class, RuleSetSettingsViewImpl.class);
        bindPresenterWidget(FieldEditPresenter.class, FieldEditView.class, FieldEditViewImpl.class);
        bindPresenterWidget(DataRetentionPresenter.class, DataRetentionView.class, DataRetentionViewImpl.class);
        bindPresenterWidget(DataRetentionRulePresenter.class,
                DataRetentionRuleView.class,
                DataRetentionRuleViewImpl.class);
        bindPresenterWidget(DataRetentionPolicyPresenter.class,
                DataRetentionPolicyView.class,
                DataRetentionPolicyViewImpl.class);
    }
}
