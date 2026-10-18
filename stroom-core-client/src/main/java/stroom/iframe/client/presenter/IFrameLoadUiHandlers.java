/*
 * Copyright 2018 Crown Copyright
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

package stroom.iframe.client.presenter;

import com.google.gwt.dom.client.NativeEvent;
import com.gwtplatform.mvp.client.UiHandlers;

public interface IFrameLoadUiHandlers extends UiHandlers {

    void onTitleChange(String title);

    /// Called for a key pressed while the frame's document has the focus, so that Stroom's shortcuts (e.g.
    /// Alt+W to close the tab) still work there. Only for a document this page can see into.
    ///
    /// @param nativeEvent The key down event.
    void onFrameKeyDown(NativeEvent nativeEvent);

    /// Called for a link clicked in the frame's document, before the frame follows it. Only for a document this
    /// page can see into.
    ///
    /// @param href The link, as the browser resolves it (absolute).
    /// @return True if the link has been opened some other way, so the frame mustn't follow it.
    boolean onFrameLinkClick(String href);
}
