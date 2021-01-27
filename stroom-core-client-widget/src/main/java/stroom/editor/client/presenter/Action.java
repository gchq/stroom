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

package stroom.editor.client.presenter;

public class Action {
    private Runnable executeHandler;
    private String text;
    private boolean available;
    private boolean defaultAvailability;

    public Action(final String text, final boolean available, final Runnable executeHandler) {
        this.text = text;
        this.available = available;
        this.defaultAvailability = available;
        this.executeHandler = executeHandler;
    }

    public void execute() {
        if (executeHandler != null) {
            executeHandler.run();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(final boolean available) {
        this.available = available;
    }

    public void setAvailable() {
        this.available = true;
    }

    public void setUnavailable() {
        this.available = false;
    }

    public void setToDefaultAvailability() {
        setAvailable(defaultAvailability);
    }

    public String getText() {
        return text;
    }
}
