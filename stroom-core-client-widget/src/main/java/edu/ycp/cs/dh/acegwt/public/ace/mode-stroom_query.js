// You can use https://ace.c9.io/tool/mode_creator.html to test the highlight rules

// When you add a new mode, you need to add a reference to the file in app.html

// First define the highlight rules

define("ace/mode/stroom_query_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;



var StroomQueryHighlightRules = function() {

    var keywords = (
        "from|select|where|filter|eval|and|or|not|sort|group|by|order|limit|having|as|" +
        "when|desc|asc|window|vis"
    );

//    var builtinConstants = (
//        "true|false"
//    );
//
//    // Created from `stroom.dashboard.expression.v1.FunctionFactory`
//    var builtinFunctions = (
//        "add|+|annotation|any|average|mean|bottom|ceiling|ceilingday|ceilinghour|ceilingminute|ceilingmonth|ceilingsecond|ceilingyear|concat|count|countgroups|countprevious|countunique|currentuser|dashboard|data|decode|decodeurl|distinct|/|divide|encodeurl|=|equals|err|exclude|extractauthorityfromuri|extractfragmentfromuri|extracthostfromuri|extractpathfromuri|extractportfromuri|extractqueryfromuri|extractschemefromuri|extractschemespecificpartfromuri|extractuserinfofromuri|false|first|floor|floorday|floorhour|floorminute|floormonth|floorsecond|flooryear|formatdate|>|greaterthan|>=|greaterthanorequalto|hash|if|include|indexof|isboolean|isdouble|iserror|isinteger|islong|isnull|isnumber|isstring|isvalue|joining|last|lastindexof|<|lessthan|<=|lessthanorequalto|link|lowercase|match|max|min|%|mod|modulo|*|multiply|negate|not|nth|null|parsedate|^|power|param|params|random|replace|round|roundday|roundhour|roundminute|roundmonth|roundsecond|roundyear|stdev|stepping|stringlength|substring|substringafter|substringbefore|-|subtract|sum|toboolean|todouble|tointeger|tolong|tostring|top|true|typeof|uppercase|variance|" +
//        "now|second|minute|hour|day|week|month|year"
//    );
//
//    var dataTypes = (
//        "int|numeric|decimal|date|varchar|char|bigint|float|double|bit|binary|text|set|timestamp|" +
//        "money|real|number|integer"
//    );

    var keywordMapper = this.createKeywordMapper({
//        "support.function": builtinFunctions,
        "keyword": keywords,
//        "constant.language": builtinConstants,
//        "storage.type": dataTypes
    }, "identifier", true);

    this.$rules = {
        "start" : [ {
            token : "comment",
            regex : "//.*$"
        },  {
            token : "comment",
            start : "/\\*",
            end : "\\*/"
        }, {
            token : "string",           // " string
            regex : '".*?"'
        }, {
            token : "string",           // ' string
            regex : "'.*?'"
        }, {
            token : "string",           // ` string (apache drill)
            regex : "`.*?`"
        }, {
            token : "keyword.operator", // Between
            regex : "between"
        }, {
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
            token : "constant.numeric", // Duration
            regex : "[+-]?\\d+[yMwdhms]"
        }, {
            token : ["support.function", "paren.lparen" ], // Functions
            regex : "(\\w+)(\\s*\\()"
        },  {
            token : keywordMapper,
            regex : "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
        }, {
            token : "keyword.operator",
            regex : "\\+|\\-|\\/|\\/\\/|%|\\^|~|<|>|<=|=>|==|!=|<>|="
        }, {
            token : "paren.lparen",
            regex : "[\\(]"
        }, {
            token : "paren.rparen",
            regex : "[\\)]"
        }, {
            token : "text",
            regex : "\\s+"
        } ]
    };
    this.normalizeRules();
};

oop.inherits(StroomQueryHighlightRules, TextHighlightRules);

exports.StroomQueryHighlightRules = StroomQueryHighlightRules;
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
//    this.snippetFileId = "ace/snippets/stroom_query";
}).call(Mode.prototype);

exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/stroom_query"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
