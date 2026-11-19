// You can use https://ace.c9.io/tool/mode_creator.html to test the highlight rules

// When you add a new mode, you need to add a reference to the file in app.html

// First define the highlight rules

define("ace/mode/stroom_stepper_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;



var StroomStepperHighlightRules = function() {

    var keywords = (
        "FATAL|ERROR|WARN"
    );

//    var builtinConstants = (
//        "xFATAL|xERROR"
//    );

    var builtinFunctions = (
        "INFO"
    );

    // var dataTypes = (

    // );

    var keywordMapper = this.createKeywordMapper({
        "support.function": builtinFunctions,
        "keyword": keywords,
//        "constant.language": builtinConstants,
        // "storage.type": dataTypes
    }, "identifier", true);

    this.$rules = {
        "start" : [ {
            token : "constant.numeric", // ISO date
            regex : "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\.\\d{3})?Z"
        }, {
            token : "string",           // " string
            regex : '".*?"'
        }, {
            token : "string",           // ' string
            regex : "'.*?'"
        }, {
            token : "constant.numeric", // float
            regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
        }, {
            token : keywordMapper,
            regex : "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
        }, {
            token : "keyword.operator",
            regex : " > "
        }, {
            token : "text",
            regex : "\\s+"
        } ]
    };
    this.normalizeRules();
};

oop.inherits(StroomStepperHighlightRules, TextHighlightRules);

exports.StroomStepperHighlightRules = StroomStepperHighlightRules;
});


// Now define the mode


define("ace/mode/stroom_stepper",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/stroom_query_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var StroomStepperHighlightRules = require("./stroom_stepper_highlight_rules").StroomStepperHighlightRules;

var Mode = function() {
    this.HighlightRules = StroomStepperHighlightRules;
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, TextMode);

(function() {
    this.lineCommentStart = "//";
    this.blockComment = {start: "/*", end: "*/"};
//    this.lineCommentStart = "--";

    this.$id = "ace/mode/stroom_stepper";
//    this.snippetFileId = "ace/snippets/stroom_stepper";
}).call(Mode.prototype);

exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/stroom_stepper"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
