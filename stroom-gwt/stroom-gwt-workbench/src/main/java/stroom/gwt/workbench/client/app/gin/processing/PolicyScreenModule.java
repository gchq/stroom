/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.processing;

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

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `PolicyModule`
/// (`stroom/receive/rules/client/gin/PolicyModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class PolicyScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(RulePresenter.class,
                RuleView.class,
                RuleViewImpl.class);
        bindPresenterWidget(RuleSetSettingsPresenter.class,
                RuleSetSettingsView.class,
                RuleSetSettingsViewImpl.class);
        bindPresenterWidget(FieldEditPresenter.class,
                FieldEditView.class,
                FieldEditViewImpl.class);
        bindPresenterWidget(DataRetentionPresenter.class,
                DataRetentionView.class,
                DataRetentionViewImpl.class);
        bindPresenterWidget(DataRetentionRulePresenter.class,
                DataRetentionRuleView.class,
                DataRetentionRuleViewImpl.class);
        bindPresenterWidget(DataRetentionPolicyPresenter.class,
                DataRetentionPolicyView.class,
                DataRetentionPolicyViewImpl.class);
    }
}

