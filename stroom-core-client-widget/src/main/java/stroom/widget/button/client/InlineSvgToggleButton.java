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

package stroom.widget.button.client;

public class InlineSvgToggleButton extends InlineSvgButton implements ButtonView {

    private boolean on;

    public void setOn(final boolean on) {
        this.on = on;
        if (on) {
            getElement().addClassName("on");
        } else {
            getElement().removeClassName("on");
        }
    }

    public boolean isOn() {
        return on;
    }

    @Override
    void onClick() {
        setOn(!this.on);
        super.onClick();
    }
}
