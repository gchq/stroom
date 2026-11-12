// You can use https://ace.c9.io/tool/mode_creator.html to test the highlight rules
// Copy from === mode_creator START === to === mode_creator END ===
// and paste into the mode_creator left pane. Paste some example sQL in the right pane

// ([\w]+?|"[\w ]+?")\s*\((\w+)\s*=\s*(\w+)

// When you add a new mode, you need to add a reference to the file in app.html

// First define the highlight rules

define("ace/mode/stroom_query_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

// === mode_creator START ===

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

var StroomQueryHighlightRules = function() {

    this.$rules = {
        "start" : [ {
            token : "comment",
            regex : "//.*$",
            caseInsensitive: true // THIS seems to affect ALL rules https://github.com/ajaxorg/ace/issues/4887
        },  {
            token : "comment",
            start : "/\\*",
            end : "\\*/"
        }, {
            // """ string, which may span lines, e.g. the SQL of a sql clause. A run of more than
            // three quotes closes on its last three, so the text can end with a quote.
            token : "string.quoted.triple",
            start : /"""/,
            end : /"{3,}/
        }, {
            token : "keyword", // From Dual | From "My View"
            regex : /(from|show)\b/,
            next : "doc-rule" // All subsequent matching done with doc-rule set
        }, {
            token : "keyword.operator", // in dictionary
            regex : /dictionary/,
            next : "doc-rule" // All subsequent matching done with doc-rule set
        }, {
            token : "keyword.operator", // Between
            regex : /between/,
            next : "between-rule" // All subsequent matching done with doc-rule set
        }, {
            include : "numerics-rule"
        }, {
            token : ["variable", "variable", "text", "variable" ], // ${xxx}
            regex : '(\\$)(\\{)(.*?)(})'
        },  {
            // A join line, e.g. 'left join "HR Users"'. Its kind is only a keyword as part of one.
            token : ["keyword", "text", "keyword"],
            regex : "(left|keep|drop)(\\s+)(join)\\b",
            next : "doc-rule" // The joined data source, as after from
        },  {
            token : "keyword",
            regex : /join\b/,
            next : "doc-rule" // The joined data source, as after from
        },  {
            token : ["keyword"],
            regex : "(from|sql|on|within|select|where|filter|eval|and|or|not|sort|group|order|"
                    + "limit|having|as|by|when|desc|asc|window|show|advance)\\b"
        },  {
            token : ["support.function", "text", "paren.lparen" ], // Un-quoted func, 'foo('
            regex : /(\w+)(\s*)(\()/
        }, {
            token : ["support.function", "text", "paren.lparen" ], // Quoted func, '"foo bar"('
            regex : /("[\w\s]+")(\s*)(\()/
        }, {
            token : "keyword.operator",
            regex : /in\b|\+|\-|\/|\/\/|%|\^|~|<|>|<=|=>|==|!=|<>|=/
        }, {
            token : "paren.lparen",
            regex : /[\(]/
        }, {
            token : "paren.rparen",
            regex : /[\)]/
        }, {
            include : "identifiers-rule"
        }, {
            token : "string.quoted.double",           // " string
            regex : /"(?:\\"|[^"])*"/
        }, {
            token : "string.quoted.single",           // ' string
            regex : /'(?:\\'|[^'])*'/
        },  {
            token : "text",
            regex : /\s+/
        }],

        "between-rule" : [ {
            include : "basic"
        }, {
            token : "keyword.operator",
            regex : /and\b/,
            next : "start"
        }, {
            include : "identifiers-rule"
        },  {
            token : "text",
            regex : /\s+/,
        },  {
            include : "numerics-rule"
        }],

        // For highlighting a Doc name, e.g. show/from/dictionary
        "doc-rule" : [ {
            // A bracketed query, e.g. 'from (from MyDoc ...)', so back to the main rules.
            token : "paren.lparen",
            regex : /\(/,
            next : "start"
        },  {
            token : "support.type", // e.g. '"My Doc"'
            regex : /"(?:\\"|[^"])*"/,
            next : "start"
        },  {
            token : "support.type", // e.g. ''My Doc''
            regex : /'(?:\\'|[^'])*'/,
            next : "start"
        },  {
            token : "support.type", // e.g. MyDoc
            regex : /\w+\b/,
            next : "start"
        },  {
            token : "text",
            regex : /\s+/
        }],

    };
    // Common regex to include above
    this.addRules( {
        "numerics-rule" : [{
            token : "constant.numeric", // date
            regex : "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z?"
        }, {
            token : "constant.numeric", // date
            regex : "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z?"
        }, {
            token : "constant.numeric", // date
            regex : "[0-9]{4}-[0-9]{2}-[0-9]{2}Z?"
        }, {
            token : "constant.numeric", // float
            regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
        }, {
            token : ["keyword.operator", "constant.numeric"], // Duration
            regex : /([+-]?)(\d+[yMwdhms])/
        }],

        "identifiers-rule" : [{
            token : "identifier",
            regex : /[a-zA-Z_$][a-zA-Z0-9_$]*\b/
        }]
    })

    this.normalizeRules();
};

oop.inherits(StroomQueryHighlightRules, TextHighlightRules);

exports.StroomQueryHighlightRules = StroomQueryHighlightRules;

// === mode_creator END ===

});


// Now define the mode


define("ace/mode/stroom_query",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/stroom_query_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var StroomQueryHighlightRules = require("./stroom_query_highlight_rules").StroomQueryHighlightRules;

var Mode = function() {
    this.HighlightRules = StroomQueryHighlightRules;
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, TextMode);

(function() {
    this.lineCommentStart = "//";
    this.blockComment = {start: "/*", end: "*/"};
//    this.lineCommentStart = "--";

    this.$id = "ace/mode/stroom_query";
    this.snippetFileId = "ace/snippets/stroom_query";
}).call(Mode.prototype);

exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/stroom_query"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
