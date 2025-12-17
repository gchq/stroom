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

package stroom.dashboard.client.vis;

import stroom.util.client.JSONUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;

import java.util.HashMap;
import java.util.Map;

public class PostMessage {

    private static final Map<Integer, FrameListener> frameListeners = new HashMap<>();
    private static PostMessage instance;

    private PostMessage() {
        attachListener();
    }

    public static PostMessage get() {
        if (instance == null) {
            instance = new PostMessage();
        }

        return instance;
    }

    private static void receiveMessage(final MessageEvent event) {
        try {
            final JSONValue json = JSONParser.parseStrict(event.getData());
            final JSONObject message = json.isObject();
            final Integer frameId = JSONUtil.getInteger(message.get("frameId"));
            if (frameId == null) {
                Window.alert("PostMessage - receiveMessage() - Null frame id");
            } else {
                final FrameListener frameListener = frameListeners.get(frameId);
                if (frameListener != null) {
                    frameListener.receiveMessage(event, message);
                }
            }
        } catch (final RuntimeException e) {
            Window.alert("PostMessage - receiveMessage() - " + e.getMessage());
        }
    }

    public void addFrameListener(final FrameListener frameListener) {
        frameListeners.put(frameListener.getFrameId(), frameListener);
    }

    public void removeFrameListener(final FrameListener frameListener) {
        frameListeners.remove(frameListener.getFrameId());
    }

    public void postMessage(final Element frame, final JSONObject json, final int frameId, final int callbackId) {
        final JSONObject message = new JSONObject();
        message.put("frameId", new JSONNumber(frameId));
        message.put("callbackId", new JSONNumber(callbackId));
        message.put("data", json);

        nativePostMessage(frame, message.toString());
    }

    public void postMessage(final Element frame, final JSONObject json, final int frameId) {
        final JSONObject message = new JSONObject();
        message.put("frameId", new JSONNumber(frameId));
        message.put("data", json);

        nativePostMessage(frame, message.toString());
    }

    private final native void nativePostMessage(final Element frame, final String json)
    /*-{
    var win = frame.contentWindow;
    if (win) {
        win.postMessage(json, '*');
    }
    }-*/;

    private final native void attachListener()
    /*-{
    var listener = function(event) {
      var origin = event.origin;
      var hostname = $wnd.location.hostname;

      // Stop this script being called from other domains.
      var eventLocation = document.createElement("a");
      eventLocation.href = origin;
      var eventHostname = eventLocation.hostname;
      if (eventHostname != hostname) {
        console.error("Ignoring event as host names do not match: hostname='" + hostname +
          "' eventHostname='" + eventHostname + "'");
        return;
      }

      var receiveMessage = @stroom.dashboard.client.vis.PostMessage::receiveMessage(*);
      receiveMessage(event);
    }

    if (window.addEventListener) {
      addEventListener("message", listener, false);
    } else {
      attachEvent("onmessage", listener);
    }
    }-*/;

    public interface FrameListener {

        int getFrameId();

        void receiveMessage(MessageEvent event, JSONObject message);
    }
}
