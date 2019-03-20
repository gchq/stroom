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

package stroom.core.ui.config;

import stroom.node.api.NodeInfo;
import stroom.task.api.AbstractTaskHandler;
import stroom.ui.config.shared.FetchUiConfigAction;
import stroom.ui.config.shared.UiConfig;
import stroom.util.BuildInfoProvider;

import javax.inject.Inject;


class FetchUiConfigHandler extends AbstractTaskHandler<FetchUiConfigAction, UiConfig> {
    private final UiConfig uiConfig;

    @Inject
    FetchUiConfigHandler(final UiConfig uiConfig,
                         final BuildInfoProvider buildInfoProvider,
                         final NodeInfo nodeInfo) {
        this.uiConfig = uiConfig;
        uiConfig.setBuildInfo(buildInfoProvider.get());
        uiConfig.setNodeName(nodeInfo.getThisNodeName());
    }

    @Override
    public UiConfig exec(final FetchUiConfigAction action) {
        return uiConfig;
    }
}
