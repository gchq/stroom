// You can use https://ace.c9.io/tool/mode_creator.html to test the highlight rules

// When you add a new mode, you need to add a reference to the file in app.html

// First define the highlight rules

define("ace/mode/stroom_hex_dump_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;



var StroomHexDumpHighlightRules = function() {

    // e.g.
    // 0000000480 f6 f6 0f 19  46 8b 2b 2a  62 dc 92 47  cf f9 d1 8a  cd da d3 11  91 0a 7c 68  91 32 e1 a4  c2 24 d8 9f  ����F�+*b��G���������↲|h�2���$��
    this.$rules = {
        "start" : [
            {
                // 0000000480
                token : "string",
                regex : /^[0-9a-f]+/
            },
            {
                //  f6 f6 0f 19  46 8b 2b 2a  62 dc 92 47  cf f9 d1 8a  cd da d3 11  91 0a 7c 68  91 32 e1 a4  c2 24 d8 9f
                token : "text",
                regex : /(?<=[0-9a-f]+)[ 0-9a-f]{106}/
            },
            {
                // ����F�+*b��G���������↲|h�2���$��
                token : "variable",
                regex : /.*$/
            }
        ]
    }

    this.normalizeRules();
};

oop.inherits(StroomHexDumpHighlightRules, TextHighlightRules);

exports.StroomHexDumpHighlightRules = StroomHexDumpHighlightRules;
});


// Now define the mode


define("ace/mode/stroom_hex_dump",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/stroom_hex_dump_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var StroomHexDumpHighlightRules = require("./stroom_hex_dump_highlight_rules").StroomHexDumpHighlightRules;

var Mode = function() {
    this.HighlightRules = StroomHexDumpHighlightRules;
};
oop.inherits(Mode, TextMode);

(function() {
    this.$id = "ace/mode/stroom_hex_dump";
}).call(Mode.prototype);

exports.Mode = Mode;
});                (function() {
                    window.require(["ace/mode/stroom_hex_dump"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
