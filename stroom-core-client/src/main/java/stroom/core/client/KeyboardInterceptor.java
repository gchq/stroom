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

package stroom.core.client;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class KeyboardInterceptor {

    private final Map<KeyTest, Command> keyTests = new HashMap<>();

    @Inject
    public KeyboardInterceptor() {
    }

    public void addKeyTest(final KeyTest keyTest, final Command command) {
        keyTests.put(keyTest, command);
    }

    public void register(final Widget widget) {
        widget.addDomHandler(event -> handleKeyEvent(event.getNativeEvent()), KeyDownEvent.getType());
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

        if (handled) {
            event.preventDefault();
            event.stopPropagation();
        }
    }


    // --------------------------------------------------------------------------------


    public interface KeyTest {

        boolean match(NativeEvent event);
    }
}
