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

package stroom.dashboard.client.vis;

import stroom.dashboard.client.vis.PostMessage.FrameListener;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.task.client.DefaultTaskListener;
import stroom.task.client.HasTaskHandlerFactory;
import stroom.task.client.TaskHandlerFactory;
import stroom.util.client.JSONUtil;
import stroom.util.shared.string.CIKey;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageSupport
        implements FrameListener, HasHandlers, HasUiHandlers<SelectionUiHandlers>, HasTaskHandlerFactory {

    private static final Map<Integer, Callback<String, Exception>> callbacks = new HashMap<>();
    private static int frameIdCounter;
    private static int callbackId;
    private final EventBus eventBus;
    private final Element frame;
    private final int frameId;
    private TaskHandlerFactory taskHandlerFactory = new DefaultTaskListener(this);

    private SelectionUiHandlers uiHandlers;

    public MessageSupport(final EventBus eventBus,
                          final Element frame) {
        this.eventBus = eventBus;
        this.frame = frame;
        frameIdCounter++;
        frameId = frameIdCounter;
    }

    @Override
    public void setUiHandlers(final SelectionUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    public void bind() {
        PostMessage.get().addFrameListener(this);
    }

    public void unbind() {
        PostMessage.get().removeFrameListener(this);
    }

    public void postMessage(final JSONObject json, final Callback<String, Exception> callback) {
        callbackId++;
        if (callbackId > 100) {
            callbackId = 1;
        }

        callbacks.put(callbackId, callback);

        PostMessage.get().postMessage(frame, json, frameId, callbackId);
    }

    public void postMessage(final JSONObject json) {
        PostMessage.get().postMessage(frame, json, frameId);
    }

    private Map<CIKey, String> toMap(final JSONObject obj) {
        if (obj != null) {
            final Map<CIKey, String> map = new HashMap<>();
            for (final String key : obj.keySet()) {
                final JSONValue v = obj.get(key);
                if (v.isString() != null) {
                    map.put(CIKey.of(key), v.isString().stringValue());
                } else {
                    map.put(CIKey.of(key), v.toString());
                }
            }
            return map;
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public void receiveMessage(final MessageEvent event, final JSONObject message) {
        final Integer callbackId = JSONUtil.getInteger(message.get("callbackId"));
        final String functionName = JSONUtil.getString(message.get("functionName"));

        if ("link".equals(functionName)) {
            final String href = JSONUtil.getString(message.get("href"));
            final String target = JSONUtil.getString(message.get("target"));
            final Hyperlink hyperlink = Hyperlink.builder().href(href).type(target).build();
            HyperlinkEvent.fire(this, hyperlink, taskHandlerFactory);

        } else if ("select".equals(functionName)) {
            final JSONValue selection = message.get("selection");
            GWT.log("Received selection from vis: " + selection.toString());

            if (uiHandlers != null) {
                final List<Map<CIKey, String>> list = new ArrayList<>();
                final JSONArray array = selection.isArray();
                if (array != null) {
                    for (int i = 0; i < array.size(); i++) {
                        list.add(toMap(array.get(i).isObject()));
                    }
                } else {
                    list.add(toMap(selection.isObject()));
                }
                uiHandlers.onSelection(list);
            }

        } else {
            // Test to see if this is a callback message.
            if (callbackId != null) {
                final Callback<String, Exception> callback = callbacks.remove(callbackId);
                if (callback != null) {
                    final String param = JSONUtil.getString(message.get("param"));
                    if ("onSuccess".equals(functionName)) {
                        callback.onSuccess(param);
                    } else if ("onFailure".equals(functionName)) {
                        callback.onFailure(new RuntimeException(param));
                    }
                }
            } else {
                final String exception = JSONUtil.getString(message.get("exception"));
                if (exception != null) {
                    Window.alert(exception);
                } else {
                    Window.alert("Unexpected message - " + message);
                }
            }
        }
    }

    @Override
    public int getFrameId() {
        return frameId;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @Override
    public void setTaskHandlerFactory(final TaskHandlerFactory taskHandlerFactory) {
        this.taskHandlerFactory = taskHandlerFactory;
    }
}
