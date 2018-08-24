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

package stroom.ui.config.server;

import stroom.node.NodeCache;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.ui.config.shared.FetchUiConfigAction;
import stroom.ui.config.shared.UiConfig;
import stroom.util.BuildInfoUtil;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchUiConfigAction.class)
class FetchUiConfigHandler extends AbstractTaskHandler<FetchUiConfigAction, UiConfig> {
    private final UiConfig uiConfig;

    @Inject
    FetchUiConfigHandler(final UiConfig uiConfig,
                         final NodeCache nodeCache) {
        this.uiConfig = uiConfig;

        uiConfig.setBuildDate(BuildInfoUtil.getBuildDate());
        uiConfig.setBuildVersion(BuildInfoUtil.getBuildVersion());
        uiConfig.setUpDate(BuildInfoUtil.getUpDate());
        uiConfig.setNodeName(nodeCache.getDefaultNode().getName());
    }

    @Override
    public UiConfig exec(final FetchUiConfigAction action) {
        return uiConfig;
    }
}
