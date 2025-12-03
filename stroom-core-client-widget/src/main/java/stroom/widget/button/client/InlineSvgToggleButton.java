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

package stroom.widget.button.client;

public class InlineSvgToggleButton extends InlineSvgButton implements ToggleButtonView {

    private boolean on;

    @Override
    public void setState(final boolean on) {
        this.on = on;
        if (on) {
            getElement().addClassName("on");
        } else {
            getElement().removeClassName("on");
        }
    }

    @Override
    public boolean getState() {
        return on;
    }

    @Override
    void onClick() {
        setState(!this.on);
        super.onClick();
    }
}
