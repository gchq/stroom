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

package stroom.app.client;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;

import javax.inject.Inject;

public class LocationManager {
    private boolean ignoreClose;

    @Inject
    public LocationManager() {
        Window.addWindowClosingHandler(event -> {
            if (!ignoreClose) {
                event.setMessage("Are you sure you want to leave Stroom?");
            } else {
                ignoreClose = false;
            }
        });
    }

    public void replace(final String newURL) {
        ignoreClose = true;
        Location.replace(newURL);
    }
}
