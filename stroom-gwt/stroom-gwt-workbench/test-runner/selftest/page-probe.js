/*
 * Copyright 2026 Crown Copyright
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

// Injected into the preview page by selftest.mjs: mounts each snippet, records the events fired
// and reads the resulting state, the same way for the workbench's port (window.__workbenchDom) and
// for the reference (window.__ref, Storybook's storybook/test).
(function () {
  'use strict';

  var EVENT_TYPES = [
    'pointerover', 'pointerenter', 'pointerout', 'pointerleave', 'pointermove', 'pointerdown', 'pointerup',
    'mouseover', 'mouseenter', 'mouseout', 'mouseleave', 'mousemove', 'mousedown', 'mouseup',
    'click', 'auxclick', 'dblclick', 'contextmenu', 'focus', 'blur', 'focusin', 'focusout',
    'keydown', 'keypress', 'keyup', 'beforeinput', 'input', 'change', 'submit', 'fileDialog'
  ];
  var MOUSE_TYPES = ['mousedown', 'mouseup', 'mousemove', 'mouseover', 'mouseout', 'mouseenter', 'mouseleave',
    'click', 'auxclick', 'dblclick', 'contextmenu'];

  var log = [];
  var host = null;

  var describe = function (node) {
    if (!node) {
      return null;
    }
    if (node === document) {
      return 'document';
    }
    if (node === window) {
      return 'window';
    }
    if (node === document.body) {
      return 'body';
    }
    if (node === document.documentElement) {
      return 'html';
    }
    if (node.nodeType === 3) {
      return '#text(' + JSON.stringify(node.textContent) + ') in ' + describe(node.parentNode);
    }
    if (node === host) {
      return 'host';
    }
    return node.id ? '#' + node.id : node.localName;
  };

  var modifiers = function (event) {
    return (event.shiftKey ? 'S' : '') + (event.ctrlKey ? 'C' : '') + (event.altKey ? 'A' : '')
      + (event.metaKey ? 'M' : '');
  };

  var record = function (event) {
    if (!host || !(event.target === document.body || event.target === document || host.contains(event.target))) {
      return;
    }
    var entry = {t: event.type, on: describe(event.target)};
    if (event.type.indexOf('pointer') === 0 || MOUSE_TYPES.indexOf(event.type) >= 0) {
      entry.b = event.button;
      entry.bs = event.buttons;
      entry.m = modifiers(event);
      if (MOUSE_TYPES.indexOf(event.type) >= 0) {
        entry.d = event.detail;
      }
      if (event.relatedTarget) {
        entry.rel = describe(event.relatedTarget);
      }
    } else if (event.type.indexOf('key') === 0) {
      entry.k = event.key;
      entry.c = event.code;
      entry.m = modifiers(event);
    } else if (event.type === 'input' || event.type === 'beforeinput') {
      entry.it = event.inputType;
      entry.data = event.data;
    } else if (event.type.indexOf('focus') === 0 || event.type === 'blur') {
      entry.rel = describe(event.relatedTarget);
    }
    log.push(entry);
  };

  EVENT_TYPES.forEach(function (type) {
    window.addEventListener(type, record, true);
  });

  var selectionState = function () {
    var selection = document.getSelection();
    if (!selection || !selection.anchorNode || !host.contains(selection.anchorNode)) {
      return null;
    }
    return {
      anchor: describe(selection.anchorNode), anchorOffset: selection.anchorOffset,
      focus: describe(selection.focusNode), focusOffset: selection.focusOffset, text: selection.toString()
    };
  };

  var state = function () {
    var elements = {};
    Array.prototype.slice.call(host.querySelectorAll('[id]')).forEach(function (el) {
      var item = {};
      if (el.value !== undefined && el.localName !== 'button' && el.localName !== 'li') {
        item.value = el.value;
      }
      if (el.checked !== undefined) {
        item.checked = el.checked;
      }
      try {
        if (el.selectionStart !== undefined && el.selectionStart !== null) {
          item.selection = [el.selectionStart, el.selectionEnd];
        }
      } catch (e) {
        // No selection
      }
      if (el.isContentEditable && el.hasAttribute('contenteditable')) {
        item.html = el.innerHTML;
      }
      if (Object.keys(item).length) {
        elements[describe(el)] = item;
      }
    });
    return {active: describe(document.activeElement), selection: selectionState(), elements: elements};
  };

  var mount = function (html, setup) {
    if (host) {
      host.remove();
    }
    if (document.activeElement && document.activeElement !== document.body) {
      document.activeElement.blur();
    }
    var selection = document.getSelection();
    if (selection) {
      selection.removeAllRanges();
    }
    host = document.createElement('div');
    host.id = 'selftest-host';
    document.body.appendChild(host);
    host.innerHTML = html;
    if (setup) {
      new Function('host', setup)(host);
    }
    log = [];
    return host;
  };

  window.__selftest = {
    mount: mount,
    state: state,
    takeLog: function () {
      var taken = log;
      log = [];
      return taken;
    },
    find: function (selector) {
      return host.querySelector(selector);
    },
    elements: function () {
      return Array.prototype.slice.call(host.querySelectorAll('*'));
    },
    describe: describe
  };
})();
