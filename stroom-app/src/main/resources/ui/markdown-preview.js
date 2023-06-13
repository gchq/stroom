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

var stroomParent;
var stroomFrameId;
var stroomOrigin;

/**
 * LISTEN TO WINDOW MESSAGES
 */
var messageListener = function(event) {
  var origin = event.origin;
  var hostname = window.location.hostname;

  // Stop this script being called from other domains.
  var eventLocation = document.createElement("a");
  eventLocation.href = origin;
  var eventHostname = eventLocation.hostname;
  if (eventHostname != hostname) {
    console.error("Ignoring event as host names do not match: hostname='"
    + hostname + "' eventHostname='" + eventHostname + "'");
    return;
  }

  console.log("received msg: " + data);

  var data = event.data;

    document.getElementById("markdown").innerHTML = data;
}

if (window.addEventListener) {
  addEventListener("message", messageListener, false);
} else {
  attachEvent("onmessage", messageListener);
}

// vim:sw=2:ts=2:et:
