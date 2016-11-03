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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Widget;

@Singleton
public class KeyboardInterceptor {
    public interface KeyTest {
        boolean match(NativeEvent event);
    }

    private final Map<KeyTest, Command> keyTests = new HashMap<>();

    @Inject
    public KeyboardInterceptor() {
    }

    public void addKeyTest(final KeyTest keyTest, final Command command) {
        keyTests.put(keyTest, command);
    }

    public void register(final Widget widget) {
        widget.addDomHandler(new KeyDownHandler() {
            @Override
            public void onKeyDown(final KeyDownEvent event) {
                handleKeyEvent(event.getNativeEvent());
            }
        }, KeyDownEvent.getType());
    }

    private void handleKeyEvent(final NativeEvent event) {
        boolean handled = false;

        for (final Entry<KeyTest, Command> entry : keyTests.entrySet()) {
            if (entry.getKey().match(event)) {
                handled = true;
                entry.getValue().execute();
                break;
            }
        }

        if (handled || block(event)) {
            event.preventDefault();
            event.stopPropagation();
        }
    }

    /**
     * Block certain key presses such as F5 and backspace.
     *
     * @param event
     * @return
     */
    private boolean block(final NativeEvent event) {
        return blockBackspace(event) || blockF5(event);
    }

    /**
     * Block certain key presses such as F5 and backspace.
     *
     * @param event
     * @return
     */
    private boolean blockBackspace(final NativeEvent event) {
        // 8 = Backspace
        final int keyCode = event.getKeyCode();
        final com.google.gwt.dom.client.Element target = event.getEventTarget().cast();
        final String tagname = target.getTagName().toLowerCase();

        final String contentEditable = target.getPropertyString("contentEditable");
        final boolean editable = "true".equals(contentEditable);

        return keyCode == 8 && !"input".equals(tagname) && !"textarea".equals(tagname)
                && !("div".equals(tagname) && editable);

    }

    /**
     * Block certain key presses such as F5 and backspace.
     *
     * @param event
     * @return
     */
    private boolean blockF5(final NativeEvent event) {
        // 116 = F5
        final int keyCode = event.getKeyCode();
        return keyCode == 116;

    }
}
