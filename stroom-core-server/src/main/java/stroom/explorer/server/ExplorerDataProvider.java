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

package stroom.explorer.server;

import java.util.Map;

import stroom.explorer.shared.ExplorerData;
import stroom.util.shared.HasType;

public interface ExplorerDataProvider extends HasType {
    void addItems(TreeModel treeModel);

    String getDisplayType();

    int getPriority();

    String getIconUrl();
}
