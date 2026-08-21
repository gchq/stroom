/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.identity.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

public interface EditAccountUiHandlers extends UiHandlers {

    void onChangePassword();

    /**
     * Release a lock applied by repeated wrong passwords. Applied straight away rather than staged into the
     * save, because it is a single unambiguous act with nothing to combine it with.
     */
    void onUnlock();

    /**
     * Make an account that has gone unused active again. Applied straight away, as for {@link #onUnlock()}.
     */
    void onReactivate();
}
