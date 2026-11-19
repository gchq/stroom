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

package stroom.document.client.editor;

import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;

/// A document's screen with tabs that a link can open directly, e.g. a link to its Documentation
/// or Permissions.
public interface HasCommonTabs {

    /// Shows one of the common tabs, if the screen has it.
    ///
    /// @param commonDocLinkTab The tab, or null for none.
    void selectCommonTab(CommonDocLinkTab commonDocLinkTab);
}
