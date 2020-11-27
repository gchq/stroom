// First define the highlight rules

define("ace/mode/stroom_expression_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;



var StroomExpressionHighlightRules = function() {

    this.$rules = {
        "start" : [
            {
                token : "string",
                regex : /'[^']*'/
            },
            {
                token : "variable",
                regex : /\$\{[^}]+\}/
//                regex : /\$\{[a-zA-Z][a-zA-Z0-9_\-: ]+\}/
            }
            // {
            //     token : "support.function",
            //     regex : /[a-zA-Z_][a-zA-Z0-9_\-]+\(/
            // }
        ]
    }

    this.normalizeRules();
};

oop.inherits(StroomExpressionHighlightRules, TextHighlightRules);

exports.StroomExpressionHighlightRules = StroomExpressionHighlightRules;
});


// Now define the mode


define("ace/mode/stroom_expression",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/stroom_expression_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var StroomExpressionHighlightRules = require("./stroom_expression_highlight_rules").StroomExpressionHighlightRules;

var Mode = function() {
    this.HighlightRules = StroomExpressionHighlightRules;
};
oop.inherits(Mode, TextMode);

(function() {
    this.$id = "ace/mode/stroom_expression";
}).call(Mode.prototype);

exports.Mode = Mode;
});                (function() {
                    window.require(["ace/mode/stroom_expression"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
