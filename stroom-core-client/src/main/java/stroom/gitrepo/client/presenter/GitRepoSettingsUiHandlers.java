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

package stroom.gitrepo.client.presenter;

import stroom.document.client.event.DirtyUiHandlers;
import stroom.task.client.TaskMonitorFactory;

/**
 * Interface to handle the button pushes from the GitRepo Settings tab.
 */
public interface GitRepoSettingsUiHandlers extends DirtyUiHandlers {

    /**
     * 'Push' button event handler.
     */
    void onGitRepoPush(TaskMonitorFactory taskMonitorFactory);

    /**
     * 'Pull' button event handler.
     */
    void onGitRepoPull(TaskMonitorFactory taskMonitorFactory);

    /**
     * 'Check for updates' button event handler.
     */
    void onCheckForUpdates(TaskMonitorFactory taskMonitorFactory);
}
