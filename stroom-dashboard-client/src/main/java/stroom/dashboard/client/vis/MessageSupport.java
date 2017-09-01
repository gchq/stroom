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

package stroom.dashboard.client.vis;

import com.google.gwt.core.client.Callback;
import com.google.gwt.dom.client.Element;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.user.client.Window;
import stroom.dashboard.client.vis.PostMessage.FrameListener;
import stroom.util.client.JSONUtil;

import java.util.HashMap;
import java.util.Map;

public class MessageSupport implements FrameListener {
    private static final Map<Integer, Callback<String, Exception>> callbacks = new HashMap<>();
    private static int frameIdCounter;
    private static int callbackId;
    private final Element frame;
    private final int frameId;

    public MessageSupport(final Element frame) {
        this.frame = frame;
        frameIdCounter++;
        frameId = frameIdCounter;
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

    @Override
    public void receiveMessage(final MessageEvent event, final JSONObject message) {
        final Integer callbackId = JSONUtil.getInteger(message.get("callbackId"));

        // Test to see if this is a callback message.
        if (callbackId != null) {
            final Callback<String, Exception> callback = callbacks.remove(callbackId);
            if (callback != null) {
                final String functionName = JSONUtil.getString(message.get("functionName"));
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
                Window.alert("Unexpected message - " + message.toString());
            }
        }
    }

    @Override
    public int getFrameId() {
        return frameId;
    }
}
