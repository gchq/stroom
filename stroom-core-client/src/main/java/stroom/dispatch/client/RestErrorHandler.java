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

package stroom.dispatch.client;

import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.event.shared.HasHandlers;

public interface RestErrorHandler {

    void onError(RestError error);

    static DefaultErrorHandler forPopup(final HasHandlers hasHandlers,
                                        final HidePopupRequestEvent event) {
        return new DefaultErrorHandler(hasHandlers, event::reset);
    }
}
