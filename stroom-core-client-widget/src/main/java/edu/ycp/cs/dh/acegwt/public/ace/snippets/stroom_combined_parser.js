define("ace/snippets/stroom_combined_parser",["require","exports","module", "ace/snippets/stroom_data_splitter", "ace/snippets/stroom_fragment_parser"], function(require, exports, module) {
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


exports.snippets = [];

// Copy of snippets from stroom_data_splitter.js
exports.snippets.push(
    {
        "tabTrigger": "csv",
        "name": "CSV Splitter",
        "content": `
<?xml version="1.0" encoding="UTF-8"?>
<dataSplitter
    xmlns="data-splitter:3"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="data-splitter:3 file://data-splitter-v3.0.xsd"
    version="3.0">

  <!-- Match each line using a new line character as the delimiter -->
  <split delimiter="\\n">

    <!-- Take the matched line (using group 1 ignores the delimiters,
    without this each match would include the new line character) -->
    <group value="\\\$1">

    <!-- Match each value separated by a comma as the delimiter -->
    <split delimiter=",">

      <!-- Output the value from group 1 (as above using group 1
        ignores the delimiters, without this each value would include
      the comma) -->
      <data value="\\\$1"/>
      \${0}
    </split>
    </group>
  </split>
</dataSplitter>
`.trim()
    },

    {
        "tabTrigger": "csvh",
        "name": "CSV Splitter with heading",
        "content": `
<?xml version="1.0" encoding="UTF-8"?>
<dataSplitter
    xmlns="data-splitter:3"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="data-splitter:3 file://data-splitter-v3.0.xsd"
    version="3.0">

  <!-- Match heading line (note that maxMatch="1" means that only the
  first line will be matched by this splitter) -->
  <split delimiter="\\n" maxMatch="1">

    <!-- Store each heading in a named list -->
    <group>
      <split delimiter=",">
        <var id="heading" />
      </split>
    </group>
  </split>

  <!-- Match each record -->
  <split delimiter="\\n">

    <!-- Take the matched line -->
    <group value="\\\$1">

      <!-- Split the line up -->
      <split delimiter=",">

        <!-- Output the stored heading for each iteration and the value
        from group 1 -->
        <data name="\\\$heading\\\$1" value="\\\$1" />
        \${0}
      </split>
    </group>
  </split>
</dataSplitter>
`.trim()
    },

    {
        "tabTrigger": "ds",
        "name": "Data Splitter Template",
        "content": `
<?xml version="1.0" encoding="UTF-8"?>
<dataSplitter
    xmlns="data-splitter:3"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="data-splitter:3 file://data-splitter-v3.0.xsd"
    version="3.0">
  \${0}
</dataSplitter>
`.trim()
    },

    {
        "tabTrigger": "nval",
        "name": "Data element with name attribute",
        "content": `
<data name="\${1}" value="\${2}"/>
\${0}
`.trim()
    },

    {
        "tabTrigger": "val",
        "name": "Data element without name attribute",
        "content": `
<data value="\${1}"/>
\${0}
`.trim()
    },

    {
        "tabTrigger": "var",
        "name": "Var element",
        "content": `
<var id="\${1}"/>
\${0}
`.trim()
    },

    {
        "tabTrigger": "spl",
        "name": "Split element",
        "content": `
<split delimiter="\${1:\\n}">
  <group value="\${2:\\\$1}">
    \${3}
  </group>
</split>
\${0}
`.trim()
    },

    {
        "tabTrigger": "gr",
        "name": "Group element",
        "content": `
<group value="\${1:\\\$1}">
  \${2}
</group>
\${0}
`.trim()
    },

    {
        "tabTrigger": "all",
        "name": "All element",
        "content": `
<all>
  \${1}
</all>
\${0}
`.trim()
    },

    {
        "tabTrigger": "reg",
        "name": "Regex element",
        "content": `
<regex \${1:dotall="true" }\${2:caseInsensitive="true" }pattern="\${3}">
  <group>
    \${0}
  </group>
</regex>
`.trim()
    },
)


exports.snippets.push(
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
)


// These snippets will only work with the 'stroom_combined_parser' mode
exports.scope = "stroom_combined_parser";

});                (function() {
                    window.require(["ace/snippets/stroom_combined_parser"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
