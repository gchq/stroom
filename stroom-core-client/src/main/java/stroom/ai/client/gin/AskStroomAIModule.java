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

package stroom.ai.client.gin;

import stroom.ai.client.AiCommandErrorPresenter;
import stroom.ai.client.AiCommandErrorPresenter.AiCommandErrorView;
import stroom.ai.client.AiCommandErrorViewImpl;
import stroom.ai.client.AiConfigGeneralPresenter;
import stroom.ai.client.AiConfigGeneralPresenter.AiConfigGeneralView;
import stroom.ai.client.AiConfigGeneralViewImpl;
import stroom.ai.client.AiConfigTableAnalysisPresenter;
import stroom.ai.client.AiConfigTableAnalysisPresenter.AiConfigTableAnalysisView;
import stroom.ai.client.AiConfigTableAnalysisViewImpl;
import stroom.ai.client.AiMemoryPresenter;
import stroom.ai.client.AiMemoryPresenter.AiMemoryView;
import stroom.ai.client.AiMemoryViewImpl;
import stroom.ai.client.AiToolApprovalsPresenter;
import stroom.ai.client.AiToolApprovalsPresenter.AiToolApprovalsView;
import stroom.ai.client.AiToolApprovalsViewImpl;
import stroom.ai.client.AiToolPoliciesPlugin;
import stroom.ai.client.AiToolPoliciesPresenter;
import stroom.ai.client.AiToolPoliciesPresenter.AiToolPoliciesView;
import stroom.ai.client.AiToolPoliciesViewImpl;
import stroom.ai.client.AiToolPolicyEditPresenter;
import stroom.ai.client.AiToolPolicyEditPresenter.AiToolPolicyEditView;
import stroom.ai.client.AiToolPolicyEditViewImpl;
import stroom.ai.client.AiToolPolicyMembersPresenter;
import stroom.ai.client.AiToolPolicyMembersPresenter.AiToolPolicyMembersView;
import stroom.ai.client.AiToolPolicyMembersViewImpl;
import stroom.ai.client.AskStroomAiConfigPresenter;
import stroom.ai.client.AskStroomAiConfigPresenter.AskStroomAiConfigView;
import stroom.ai.client.AskStroomAiConfigViewImpl;
import stroom.ai.client.AskStroomAiPresenter;
import stroom.ai.client.AskStroomAiPresenter.AskStroomAiView;
import stroom.ai.client.AskStroomAiTabPresenter;
import stroom.ai.client.AskStroomAiTabPresenter.AskStroomAiTabView;
import stroom.ai.client.AskStroomAiTabViewImpl;
import stroom.ai.client.AskStroomAiViewImpl;
import stroom.ai.client.DownloadChatPresenter;
import stroom.ai.client.DownloadChatPresenter.DownloadChatView;
import stroom.ai.client.DownloadChatViewImpl;
import stroom.core.client.gin.PluginModule;

public class AskStroomAIModule extends PluginModule {

    @Override
    protected void configure() {
        bindPresenter(
                AskStroomAiPresenter.class,
                AskStroomAiView.class,
                AskStroomAiViewImpl.class,
                AskStroomAiPresenter.AskStroomAiProxy.class);
        bindPresenterWidget(
                AskStroomAiConfigPresenter.class,
                AskStroomAiConfigView.class,
                AskStroomAiConfigViewImpl.class);
        bindPresenterWidget(
                AiConfigGeneralPresenter.class,
                AiConfigGeneralView.class,
                AiConfigGeneralViewImpl.class);
        bindPresenterWidget(
                AiConfigTableAnalysisPresenter.class,
                AiConfigTableAnalysisView.class,
                AiConfigTableAnalysisViewImpl.class);
        bindPresenterWidget(
                AskStroomAiTabPresenter.class,
                AskStroomAiTabView.class,
                AskStroomAiTabViewImpl.class);
        bindPresenterWidget(
                AiCommandErrorPresenter.class,
                AiCommandErrorView.class,
                AiCommandErrorViewImpl.class);
        bindPresenterWidget(
                DownloadChatPresenter.class,
                DownloadChatView.class,
                DownloadChatViewImpl.class);
        bindPresenterWidget(
                AiMemoryPresenter.class,
                AiMemoryView.class,
                AiMemoryViewImpl.class);
        bindPresenterWidget(
                AiToolApprovalsPresenter.class,
                AiToolApprovalsView.class,
                AiToolApprovalsViewImpl.class);

        // Security > MCP Tool Policies
        bindPlugin(AiToolPoliciesPlugin.class);
        bindPresenterWidget(
                AiToolPoliciesPresenter.class,
                AiToolPoliciesView.class,
                AiToolPoliciesViewImpl.class);
        bindPresenterWidget(
                AiToolPolicyEditPresenter.class,
                AiToolPolicyEditView.class,
                AiToolPolicyEditViewImpl.class);
        bindPresenterWidget(
                AiToolPolicyMembersPresenter.class,
                AiToolPolicyMembersView.class,
                AiToolPolicyMembersViewImpl.class);
    }
}
