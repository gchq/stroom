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

package stroom.explorer.server;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import javax.inject.Inject;

/**
 * Configures the context for process integration tests.
 * <p>
 * Reuses production configurations but defines its own component scan.
 * <p>
 * This configuration relies on @ActiveProfile(StroomSpringProfiles.TEST) being
 * applied to the tests.
 */

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@ComponentScan(basePackages = {
        "stroom.explorer.server"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class)})
public class ExplorerConfiguration {
    @Inject
    public ExplorerConfiguration(final ExplorerActionHandlers explorerActionHandlers,
                                 final SystemExplorerActionHandler systemExplorerActionHandler,
                                 final FolderExplorerActionHandler folderExplorerActionHandler) {
        explorerActionHandlers.add(0, SystemExplorerActionHandler.SYSTEM, SystemExplorerActionHandler.SYSTEM, systemExplorerActionHandler);
        explorerActionHandlers.add(1, FolderExplorerActionHandler.FOLDER, FolderExplorerActionHandler.FOLDER, folderExplorerActionHandler);
    }
}
