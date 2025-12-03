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

package stroom.widget.util.client;

import com.google.gwt.user.client.Timer;

import java.util.Objects;

public class DoubleClickTester {

    public static final int DOUBLE_CLICK_PERIOD = 500;
    private final Timer doubleClickTimer;
    private Object object;

    public DoubleClickTester() {
        doubleClickTimer = new Timer() {
            @Override
            public void run() {
                object = null;
            }
        };
    }

    public boolean isDoubleClick(final Object object) {
        doubleClickTimer.cancel();
        if (object != null && Objects.equals(this.object, object)) {
            this.object = null;
            return true;
        }

        this.object = object;

        if (object != null) {
            doubleClickTimer.schedule(DOUBLE_CLICK_PERIOD);
        }

        return false;
    }

    public void clear() {
        doubleClickTimer.cancel();
        this.object = null;
    }
}
