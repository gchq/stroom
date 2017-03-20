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

public class Option {
    private ChangeHandler changeHandler;
    private String text;
    private boolean on;
    private boolean available;

    public Option(final String text, final boolean on, final boolean available, final ChangeHandler changeHandler) {
        this.text = text;
        this.on = on;
        this.available = available;
        this.changeHandler = changeHandler;
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(final boolean on) {
        if (this.on != on) {
            this.on = on;
            if (changeHandler != null) {
                changeHandler.onChange(on);
            }
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(final boolean available) {
        this.available = available;
    }

    public String getText() {
        if (on) {
            return "Hide " + text;
        }
        return "Show " + text;
    }

    public boolean isOk() {
        return available && on;
    }

    public interface ChangeHandler {
        void onChange(boolean setting);
    }
}
