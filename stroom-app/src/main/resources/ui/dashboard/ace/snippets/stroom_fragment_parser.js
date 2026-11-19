define("ace/snippets/stroom_fragment_parser",["require","exports","module"], function(require, exports, module) {
"use strict";

// $1 / ${1} is numbered tab position (with 1 being first) with no default text.
// ${1:defaultText} is a numbered tab position with default text that is replaced on typing
// $0 is final tab position
// tabTrigger is the bit you type then hit tab to trigger the snippet.

// This file needs to be included in app.html as a <script> tag


// NOTE: There are two levels of escaping going on here, first in the .js backtick block
// and second in the snippet. '\' and '$' are special in both the backticks and the snippet parsing.
// A snippet tab stop is like '\${0}'
// A literal backslash in the rendered snippet is '\\'
// A literal dollar in the rendered snippet is '\\\$' ('\\' to make a literal '\' which escapes the escaped dollar '\$')

exports.snippets = [

    {
        "tabTrigger": "evt",
        "name": "Events fragment template",
        "content": `
<?xml version="1.1" encoding="utf-8"?>
<!DOCTYPE Events [
<!ENTITY fragment SYSTEM "fragment">]>
<Events
    xmlns="event-logging:\${1:3}"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="event-logging:\${1} file://event-logging-v\${2:3.4.2}.xsd"
    version="\${2}">
&fragment;
</records>
\${0}
`.trim()
    },

    {
        "tabTrigger": "rec",
        "name": "Records fragment template",
        "content": `
<?xml version="1.1" encoding="utf-8"?>
<!DOCTYPE Records [
<!ENTITY fragment SYSTEM "fragment">]>
<records
    xmlns="records:\${1:2}"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="records:\${1} file://records-v\${2:2.0}.xsd"
    version="\${2}">
&fragment;
</records>
\${0}
`.trim()
    },

];

// These snippets will only work with the 'stroom_fragment_parser' mode
exports.scope = "stroom_fragment_parser";

});                (function() {
                    window.require(["ace/snippets/stroom_fragment_parser"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
