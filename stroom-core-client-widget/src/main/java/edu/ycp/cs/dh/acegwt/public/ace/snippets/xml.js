define("ace/snippets/xml",["require","exports","module"], function(require, exports, module) {
"use strict";

// These snippets were added in by at055612 from
// https://github.com/ajaxorg/ace/blob/master/lib/ace/snippets/xslt.snippets
// This is a temporary hack to get some useful snippets in the editor.
// To get these to work I had to add the following to mode-xml.js
//   this.snippetFileId = "ace/snippets/xml";
// If the snippet content is not indented with tabs the SnippetManager
// can't parse them.
// See also https://github.com/gchq/stroom/issues/1745

// $1 / ${1} is numbered tab position (with 1 being first) with no default text.
// ${1:defaultText} is a numbered tab position with default text that is replaced on typing
// $0 is final tab position
// tabTrigger is the bit you type then hit tab to trigger the snippet.
// If using javascript template literals for multi-line strings, you must escape
// '$' with a '\', i.e. '\${'

// This file needs to be included in app.html as a <script> tag

exports.snippets = [
    {
        "tabTrigger": "apply-templates with-param",
        "name": "apply-templates with-param",
        "content": `
<xsl:apply-templates select="\${1:*}">
  <xsl:with-param name="\${2:param}">\${3}</xsl:with-param>\${4}
</xsl:apply-templates>
\${0}`
    },
    {
    "tabTrigger": "apply-templates sort-by",
    "name": "apply-templates sort-by",
    "content": `
<xsl:apply-templates select="\${1:*}">
  <xsl:sort select="\${2:node}" order="\${3:ascending}" data-type="\${4:text}">\${5}
</xsl:apply-templates>
\${0}`
    },
    {
    "tabTrigger": "apply-templates plain",
    "name": "apply-templates plain",
    "content": `
<xsl:apply-templates select="\${1:*}" />
\${0}`
    },
    {
    "tabTrigger": "attribute blank",
    "name": "attribute blank",
    "content": `
<xsl:attribute name="\${1:name}">\${2}</xsl:attribute>
\${0}`
    },
    {
    "tabTrigger": "attribute value-of",
    "name": "attribute value-of",
    "content": `
<xsl:attribute name="\${1:name}">
  <xsl:value-of select="\${2:*}" />
</xsl:attribute>
\${0}`
    },
    {
    "tabTrigger": "call-template",
    "name": "call-template",
    "content": `
<xsl:call-template name="\${1:template}" />
\$0`
    },
    {
    "tabTrigger": "call-template with-param",
    "name": "call-template with-param",
    "content": `
<xsl:call-template name="\${1:template}">
  <xsl:with-param name="\${2:param}">\${3}</xsl:with-param>\${4}
</xsl:call-template>
\${0}`
    },
    {
    "tabTrigger": "choose",
    "name": "choose",
    "content": `
<xsl:choose>
  <xsl:when test="\${1:value}">
    \${2}
  </xsl:when>\${3}
</xsl:choose>
\$0`
    },
    {
    "tabTrigger": "copy-of",
    "name": "copy-of",
    "content": `
<xsl:copy-of select="\${1:*}" />
\${0}`
    },
    {
    "tabTrigger": "for-each",
    "name": "for-each",
    "content": `
<xsl:for-each select="\${1:*}">
  \${2}
</xsl:for-each>
\${0}`
    },
    {
    "tabTrigger": "if",
    "name": "if",
    "content": `
<xsl:if test="\${1:test}">
  \${2}
</xsl:if>
\${0}`
    },
    {
    "tabTrigger": "import",
    "name": "import",
    "content": `
<xsl:import href="\${1:stylesheet}" />
\${0}`
    },
    {
    "tabTrigger": "include",
    "name": "include",
    "content": `
<xsl:include href="\${1:stylesheet}" />
\${0}`
    },
    {
    "tabTrigger": "otherwise",
    "name": "otherwise",
    "content": `
<xsl:otherwise>
  \${1}
</xsl:otherwise>
\$0`
    },
    {
    "tabTrigger": "param",
    "name": "param",
    "content": `
<xsl:param name="\${1:name}">
  \${2}
</xsl:param>
\${0}`
    },
    {
    "tabTrigger": "stylesheet",
    "name": "stylesheet",
    "content": `
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  \${0}
</xsl:stylesheet>`
    },
    {
    "tabTrigger": "template",
    "name": "template",
    "content": `
<xsl:template match="\${1:*}">
  \${3}
</xsl:template>
\$0`
    },
    {
    "tabTrigger": "template named",
    "name": "template named",
    "content": `
<xsl:template name="\${1:name}">
  \${2}
</xsl:template>
\$0`
    },
    {
    "tabTrigger": "text",
    "name": "text",
    "content": `
<xsl:text>\${1}</xsl:text>
\$0`
    },
    {
    "tabTrigger": "value-of",
    "name": "value-of",
    "content": `
<xsl:value-of select="\${1:*}" />
\${0}`
    },
    {
    "tabTrigger": "variable blank",
    "name": "variable blank",
    "content": `
<xsl:variable name="\${1:name}">
  \${2}
</xsl:variable>`
    },
    {
    "tabTrigger": "variable select",
    "name": "variable select",
    "content": `
<xsl:variable select="\${1:*}" />\${2} `
    },
    {
    "tabTrigger": "when",
    "name": "when",
    "content": `
<xsl:when test="\${1:test}">
  \${2}
</xsl:when>`
    },
    {
    "tabTrigger": "with-param",
    "name": "with-param",
    "content": `
<xsl:with-param name="\${1:name}">\${2}</xsl:with-param>`
    },
    {
    "tabTrigger": "with-param select",
    "name": "with-param select",
    "content": `
<xsl:with-param name="\${1:name}" select="\${2:*}" />`
    },
];

exports.scope = "xml";

});                (function() {
                    window.require(["ace/snippets/xml"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();

// vim:sw=2:ts=2:noet:
