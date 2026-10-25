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

// DEPRECATED - HERE FOR BACKWARD COMPATIBILITY
// Send a message to Stroom to open a link
// e.g. `stroomLink('type=Dashboard&uuid=<TARGET_DASHBOARD_UUID>&title=title&params=userId%3Duser2', 'DASHBOARD')`
var stroomLink = function(href, target) {
    target = (typeof target === 'undefined') ? 'browser' : target;

    if (stroomParent && stroomFrameId && stroomOrigin) {
        var obj = {
          frameId : stroomFrameId,
          functionName : 'link',
          href : String(encodeURI(href)),
          target : String(encodeURI(target)),
        };

        var message = JSON.stringify(obj);
        stroomParent.postMessage(message, stroomOrigin);
    }
}

// True once the current visualisation has sent its own render report.
var visReportsItself = false;
var lastReportJson = null;

// Send a render report to Stroom, unless it is the same as the last one.
var postReport = function(report) {
    var json = JSON.stringify(report);
    if (json === lastReportJson) {
        return;
    }
    lastReportJson = json;

    if (stroomParent && stroomFrameId && stroomOrigin) {
        var obj = {
          frameId : stroomFrameId,
          functionName : 'report',
          report : report,
        };

        var message = JSON.stringify(obj);
        stroomParent.postMessage(message, stroomOrigin);
    }
}

!function() {
    var stroom = {};

    function addUrlParam(url, param, value) {
        if (param && value) {
            if (url.length == 0) {
                url += "?";
            } else {
                url += "&";
            }
            url += param + "=" + encodeURI(value);
        }
        return url;
    }

    stroom.select = function(selection) {
        if (stroomParent && stroomFrameId && stroomOrigin) {
            var obj = {
              frameId : stroomFrameId,
              functionName : 'select',
              selection : selection,
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // Send a message to Stroom to open a link
    // e.g. `stroom.link('type=Dashboard&uuid=<TARGET_DASHBOARD_UUID>&title=title&params=userId%3Duser2', 'DASHBOARD')`
    stroom.link = function(href, target) {
        target = (typeof target === 'undefined') ? 'browser' : target;

        if (stroomParent && stroomFrameId && stroomOrigin) {
            var obj = {
              frameId : stroomFrameId,
              functionName : 'link',
              href : String(encodeURI(href)),
              target : String(encodeURI(target)),
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // Send Stroom a report on how the visualisation rendered, e.g. what it drew, what it dropped
    // or why it failed. Stroom keeps the latest report for the visualisation.
    // e.g. `stroom.report({status: 'ok', series: 2, points: 40, droppedRows: {}, warnings: []})`
    stroom.report = function(report) {
        visReportsItself = true;
        postReport(report);
    };

    stroom.dashboard = function(uuid, params, queryOnOpen) {
       var url = "";
       url = addUrlParam(url, "uuid", uuid);
       url = addUrlParam(url, "params", params);
       url = addUrlParam(url, "queryOnOpen", queryOnOpen);
       stroom.link(url, "dashboard");
    };

    stroom.annotation = function(annotationId, streamId, eventId, title, subject, status, assignedTo, comment) {
       var url = "";
       url = addUrlParam(url, "annotationId", annotationId);
       url = addUrlParam(url, "streamId", streamId);
       url = addUrlParam(url, "eventId", eventId);
       url = addUrlParam(url, "title", title);
       url = addUrlParam(url, "subject", subject);
       url = addUrlParam(url, "status", status);
       url = addUrlParam(url, "assignedTo", assignedTo);
       url = addUrlParam(url, "comment", comment);
       stroom.link(url, "annotation");
    };

    stroom.stepping = function(id, partNo, recordNo) {
       var url = "";
       url = addUrlParam(url, "id", id);
       url = addUrlParam(url, "partNo", partNo);
       url = addUrlParam(url, "recordNo", recordNo);
       stroom.link(url, "stepping");
    };

    stroom.data = function(id, partNo, recordNo, lineFrom, colFrom, lineTo, colTo) {
       var url = "";
       url = addUrlParam(url, "id", id);
       url = addUrlParam(url, "partNo", partNo);
       url = addUrlParam(url, "recordNo", recordNo);
       url = addUrlParam(url, "lineFrom", lineFrom);
       url = addUrlParam(url, "colFrom", colFrom);
       url = addUrlParam(url, "lineTo", lineTo);
       url = addUrlParam(url, "colTo", colTo);
       stroom.link(url, "data");
    };

    this.stroom = stroom;
}();

/**
 * AN OBJECT TO SEND CALLBACK MESSAGES TO Stroom
 */
function Callback(event, frameId, callbackId) {
  stroomParent = event.source;
  stroomFrameId = frameId;
  stroomOrigin = event.origin;

  var event = event;
  var frameId = frameId;
  var callbackId = callbackId;
  var sendCallback = function(event, frameId, callbackId, functionName, param) {
    var obj = {
      frameId : frameId,
      callbackId : callbackId,
      functionName : functionName,
      param : param
    };

    var message = JSON.stringify(obj);

    event.source.postMessage(message, event.origin);
  }

  this.onSuccess = function(message) {
    sendCallback(event, frameId, callbackId, "onSuccess", message);
  };
  this.onFailure = function(ex) {
    sendCallback(event, frameId, callbackId, "onFailure", ex);
  };
}

/**
 * AN OBJECT TO PERFORM SCRIPT INJECTION
 */
function ScriptInjector() {
  var makeScriptElement = function(doc) {
    var script = doc.createElement("script");
    script.setAttribute("type", "text/javascript");
    script.setAttribute("charset", "UTF-8");
    return script;
  }

  var attachListeners = function(scriptElement, url, callback, removeTag) {
    var clearCallbacks = function() {
      scriptElement.onerror = scriptElement.onreadystatechange = scriptElement.onload = null;
      if (removeTag) {
        scriptElement.parentNode.removeChild(scriptElement);
      }
    };
    scriptElement.onload = function() {
      clearCallbacks();
      if (callback) {
        callback.onSuccess(url);
      }
    };
    // or possibly more portable script_tag.addEventListener('error',
    // function(){...}, true);
    scriptElement.onerror = function() {
      clearCallbacks();
      if (callback) {
        var ex = "onerror() called.";
        callback.onFailure(ex);
      }
    };
    scriptElement.onreadystatechange = function() {
      if (/loaded|complete/.test(scriptElement.readyState)) {
        scriptElement.onload();
      }
    };
  }

  this.inject = function(url, callback) {
    var wnd = window;
    var doc = wnd.document;

    var scriptElement = makeScriptElement(doc);
    (doc.head || doc.getElementsByTagName("head")[0]).appendChild(scriptElement);
    attachListeners(scriptElement, url, callback, false);
    scriptElement.src = url;
  }
}

/**
 * The message of an error, which may be an Error or a plain string.
 */
function errorMessage(ex) {
  if (ex && ex.message) {
    return ex.message;
  }
  return String(ex);
}

/**
 * AN OBJECT TO LOAD AND INTERACT WITH VISUALISATIONS
 */
function VisualisationManager() {
  var vis = null;
  var currentContext = null;
  var currentSettings = null;
  var currentData = null;
  var running = false;

  var update = function(callback) {
    if (vis && currentData) {
      try {
        vis.setData(currentContext, currentSettings, currentData);
        scheduleDomCheck();
      } catch (ex) {
        // Visualisations that don't report their own errors still get one reported: its kind only, as its
        // message may hold the data that broke it.
        console.error(ex);
        postReport({status : "error", error : (ex && ex.name ? ex.name : "Error")
            + " in the visualisation's script (see the browser console)"});
      }
    }
  };

  var domCheckTimer = null;

  // For visualisations that don't send their own report, report what the DOM holds a moment after
  // each update: whether anything was drawn and its size, never the data values or labels.
  var scheduleDomCheck = function() {
    if (domCheckTimer) {
      clearTimeout(domCheckTimer);
    }
    domCheckTimer = setTimeout(function() {
      domCheckTimer = null;
      if (!vis || !vis.element || visReportsItself) {
        return;
      }
      var element = vis.element;
      var rect = element.getBoundingClientRect();
      var svgCount = element.querySelectorAll("svg").length;
      var canvasCount = element.querySelectorAll("canvas").length;
      var elementCount = element.querySelectorAll("*").length;
      var drawn = (svgCount + canvasCount) > 0 && rect.width > 0 && rect.height > 0;
      postReport({
        status : drawn ? "ok" : "nothing-drawn",
        source : "dom",
        svgCount : svgCount,
        canvasCount : canvasCount,
        elementCount : elementCount,
        width : Math.round(rect.width),
        height : Math.round(rect.height)
      });
    }, 1500);
  };

  var injectNextScript = function(scripts, callback) {
    if (scripts.length > 0) {
      var script = scripts.splice(0, 1)[0];
      var cb = {
        onSuccess : function(message) {
          injectNextScript(scripts, callback);
        },
        onFailure : function(ex) {
          callback.onFailure("Failed to inject script '" + script.name + "' - "
              + errorMessage(ex));
        }
      };

      new ScriptInjector().inject(script.url, cb);

    } else {
      callback.onSuccess(null);
    }
  };

  var doStart = function(callback) {
    running = true;
    if (vis && vis.start) {
      vis.start();
    }
  };

  var doEnd = function(callback) {
    if (vis && vis.end) {
      vis.end();
    }
    running = false;
  };

  this.injectScripts = function(scripts, callback) {
    injectNextScript(scripts, callback);
  };

  this.start = function(callback) {
    doStart();
  };

  this.end = function(callback) {
    doEnd();
  };

  this.setVisType = function(type, className, callback) {
    visReportsItself = false;
    lastReportJson = null;
    try {
      vis = eval("new " + type + "()");
      callback.onSuccess(null);
    } catch (ex) {
      callback.onFailure(errorMessage(ex));
      postReport({status : "error", error : "Unable to create '" + type + "' - " + errorMessage(ex)});
    }

    if (vis && vis.element) {
      document.body.innerHTML = "";
      document.body.appendChild(vis.element);
      vis.element.className = className;

      if (running) {
        doStart();
        update();
      }
    }
  };

  this.setClassName = function(className, callback) {
    if (vis && vis.element) {
      vis.element.className = className;
    }
  };

  this.setData = function(context, settings, data, callback) {
    currentContext = context;
    currentSettings = settings;
    currentData = data;

    update();
  };

  this.resize = function(callback) {
    if (vis) {
      vis.resize();
    }
  };
}

/**
 * GLOBAL VISUALISATION MANAGER
 */
var visualisationManager = new VisualisationManager();

/**
 * LISTEN TO WINDOW MESSAGES
 */
var messageListener = function(event) {
  var sendMessage = function(event, frameId, data) {
    var obj = {
      frameId : frameId,
      data : data
    }

    var message = JSON.stringify(obj);

    event.source.postMessage(message, event.origin);
  };

  var origin = event.origin;
  var hostname = window.location.hostname;

  // Stop this script being called from other domains.
  var eventLocation = document.createElement("a");
  eventLocation.href = origin;
  var eventHostname = eventLocation.hostname;
  if (eventHostname != hostname) {
    console.error("Ignoring event as host names do not match: hostname='" + hostname + "' eventHostname='" + eventHostname + "'");
    return;
  }

  var json = JSON.parse(event.data);

  if (json.data) {
    var frameId;
    var callbackId;

    if (json.frameId) {
      frameId = json.frameId;
    }
    if (json.callbackId) {
      callbackId = json.callbackId;
    }

    var callback = new Callback(event, frameId, callbackId);
    var params = json.data.params;
    if (!params) {
        params = [];
    }
    params.push(callback);

    eval(json.data.functionName + ".apply(this, params);");
  }
}

if (window.addEventListener) {
  addEventListener("message", messageListener, false);
} else {
  attachEvent("onmessage", messageListener);
}
