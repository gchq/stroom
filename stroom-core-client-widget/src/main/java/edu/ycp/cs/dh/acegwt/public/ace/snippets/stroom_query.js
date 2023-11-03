define("ace/snippets/stroom_query",["require","exports","module"], function(require, exports, module) {
"use strict";

// $1 / ${1} is numbered tab position (with 1 being first) with no default text.
// ${1:defaultText} is a numbered tab position with default text that is replaced on typing
// $0 is final tab position
// tabTrigger is the bit you type then hit tab to trigger the snippet.

// This file needs to be included in app.html as a <script> tag

exports.snippets = [
    {
        // eval StreamId = first(StreamId)
        // $0
        "tabTrigger": "str",
        "name": "Eval first StreamId",
        "content": "eval StreamId = first(StreamId)\n$0",
    },
    {
        // eval EventId = first(EventId)
        // $0
        "tabTrigger": "evt",
        "name": "Eval first EventId",
        "content": "eval EventId = first(EventId)\n$0",
    },
    {
        // eval StreamId = first(StreamId)
        // eval EventId = first(EventId)
        // $0
        "tabTrigger": "ids",
        "name": "Eval first Stream/EventIds",
        "content": `
eval StreamId = first(StreamId)
eval EventId = first(EventId)
$0`,
    },
    {
        "tabTrigger": "first",
        "name": "Eval first first value",
        "content": "eval ${1:field_name} = first(${1})\n$0",
    },
];

// These snippets will only work with the 'stroom_query' mode
exports.scope = "stroom_query";

});                (function() {
                    window.require(["ace/snippets/stroom_query"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
