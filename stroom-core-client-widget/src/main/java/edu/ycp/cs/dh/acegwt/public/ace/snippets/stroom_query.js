define("ace/snippets/stroom_query",["require","exports","module"], function(require, exports, module) {
"use strict";

exports.snippetText = "# stroom_query\n\
snippet str\n\
\teval StreamId = first(StreamId)\n\
\t${0}\n\
snippet evt\n\
\teval EventId = first(EventId)\n\
\t${0}\n\
snippet ids\n\
\teval StreamId = first(StreamId)\n\
\teval EventId = first(EventId)\n\
\t${0}\n\
";

exports.scope = "stroom_query";

});                (function() {
                    window.require(["ace/snippets/stroom_query"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
