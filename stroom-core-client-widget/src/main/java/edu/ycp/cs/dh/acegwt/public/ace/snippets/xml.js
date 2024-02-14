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
        "tabTrigger": "wapply",
        "name": "apply-templates with-param",
        "content": `
<xsl:apply-templates select="\${1:*}">
  <xsl:with-param name="\${2:param}">\${3}</xsl:with-param>
  \${0}
</xsl:apply-templates>`.trim()
    },

    {
        "tabTrigger": "applysort",
        "name": "apply-templates sort-by",
        "content": `
<xsl:apply-templates select="\${1:*}">
  <xsl:sort select="\${2:node}" order="\${3:ascending}" data-type="\${4:text}">\${5}
</xsl:apply-templates>
\${0}`.trim()
    },

    {
        "tabTrigger": "apply",
        "name": "apply-templates plain",
        "content": `
<xsl:apply-templates select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "attr",
        "name": "attribute blank",
        "content": `
<xsl:attribute name="\${1:name}">\${2}</xsl:attribute>
\${0}`.trim()
    },

    {
        "tabTrigger": "attrval",
        "name": "attribute value-of",
        "content": `
<xsl:attribute name="\${1:name}">
  <xsl:value-of select="\${2:*}" />
</xsl:attribute>
\${0}`.trim()
    },

    {
        "tabTrigger": "call",
        "name": "call-template",
        "content": `
<xsl:call-template name="\${1:template}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "wcall",
        "name": "call-template with-param",
        "content": `
<xsl:call-template name="\${1:template}">
  <xsl:with-param name="\${2:param}">\${3}</xsl:with-param>\${4}
</xsl:call-template>
\${0}`.trim()
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
\${0}`.trim()
    },

    {
        "tabTrigger": "copyof",
        "name": "copy-of",
        "content": `
<xsl:copy-of select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "elem",
        "name": "element blank",
        "content": `
<xsl:element name="\${1:name}">
  \${2}
</xsl:element>
\${0}`.trim()
    },

    {
        "tabTrigger": "foreach",
        "name": "for-each",
        "content": `
<xsl:for-each select="\${1:*}">
  \${2}
</xsl:for-each>
\${0}`.trim()
    },

    {
        "tabTrigger": "if",
        "name": "if",
        "content": `
<xsl:if test="\${1:test}">
  \${2}
</xsl:if>
\${0}`.trim()
    },

    {
        "tabTrigger": "imp",
        "name": "import",
        "content": `
<xsl:import href="\${1:stylesheet}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "inc",
        "name": "include",
        "content": `
<xsl:include href="\${1:stylesheet}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "otherwise",
        "name": "otherwise",
        "content": `
<xsl:otherwise>
  \${1}
</xsl:otherwise>
\$0`.trim()
    },

    {
        "tabTrigger": "param",
        "name": "param",
        "content": `
<xsl:param name="\${1:name}">
  \${2}
</xsl:param>
\${0}`.trim()
    },

    {
        "tabTrigger": "style",
        "name": "stylesheet",
        "content": `
<xsl:stylesheet version="1.0" xmlns="\${1}" xpath-default-namespace="\${2:\${1}}" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  \${0}
</xsl:stylesheet>`.trim()
    },

    {
        "tabTrigger": "temp",
        "name": "template",
        "content": `
<xsl:template match="\${1:*}">
  \${2}
</xsl:template>
\$0`.trim()
    },

    {
        "tabTrigger": "ntemp",
        "name": "template named",
        "content": `
<xsl:template name="\${1:name}">
  \${2}
</xsl:template>
\$0`.trim()
    },

    {
        "tabTrigger": "text",
        "name": "text",
        "content": `
<xsl:text>\${1}</xsl:text>
\$0`.trim()
    },

    {
        "tabTrigger": "valof",
        "name": "value-of",
        "content": `
<xsl:value-of select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "var",
        "name": "variable blank",
        "content": `
<xsl:variable name="\${1:name}">
  \${0}
</xsl:variable>`.trim()
    },

    {
        "tabTrigger": "varsel",
        "name": "variable select",
        "content": `
<xsl:variable select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "when",
        "name": "when",
        "content": `
<xsl:when test="\${1:test}">
  \${0}
</xsl:when>`.trim()
    },

    {
        "tabTrigger": "wparam",
        "name": "with-param",
        "content": `
<xsl:with-param name="\${1:name}">\${2}</xsl:with-param>
\${0}`.trim()
    },

    {
        "tabTrigger": "wparamsel",
        "name": "with-param select",
        "content": `
<xsl:with-param name="\${1:name}" select="\${2:*}" />
\${0}`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "ident",
        "name": "identity skeleton",
        "content": `
<xsl:stylesheet version="1.0" xpath-default-namespace="\${1:event-logging:3}" xmlns="\${2:\${1}}" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- Whenever you match any node or any attribute -->
  <xsl:template match="node( )|@*">

    <!-- Copy the current node -->
    <xsl:copy>

      <!-- Including any attributes it has and any child nodes -->
      <xsl:apply-templates select="@*|node( )"/>
    </xsl:copy>
  </xsl:template>

  \${0}
</xsl:stylesheet>`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "recident",
        "name": "records identity skeleton",
        "content": `
<xsl:stylesheet version="1.0" xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- Match Root Object -->
  <xsl:template match="records">
    <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="event-logging:3 file://event-logging-v3.4.2.xsd" Version="3.4.2">
      <xsl:apply-templates />
    </Events>
  </xsl:template>
  <xsl:template match="record">
    <Event>
      <EventTime>
        <TimeCreated>\${1:time}</TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>\${2:name}</Name>
          <Environment>\${3:environment}</Environment>
        </System>
        <Generator>\${4:generator}</Generator>
        <Device>\${5:device}</Device>
      </EventSource>
      <EventDetail>
        <TypeId>\${6:type}</TypeId>
        \${0}
        <xsl:apply-templates />
      </EventDetail>
    </Event>
  </xsl:template>

  <!-- Whenever you match any node or any attribute -->
  <xsl:template match="node( )|@*">

    <!-- Copy the current node -->
    <xsl:copy>

      <!-- Including any attributes it has and any child nodes -->
      <xsl:apply-templates select="@*|node( )" />
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "evtident",
        "name": "events identity skeleton",
        "content": `
<xsl:stylesheet version="1.0" xpath-default-namespace="event-logging:3" xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- Match Root Object -->
  <xsl:template match="Events">
    <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="event-logging:3 file://event-logging-v3.4.2.xsd" Version="3.4.2">
      <xsl:apply-templates />
    </Events>
  </xsl:template>
  \${0}

  <!-- Whenever you match any node or any attribute -->
  <xsl:template match="node( )|@*">

    <!-- Copy the current node -->
    <xsl:copy>

      <!-- Including any attributes it has and any child nodes -->
      <xsl:apply-templates select="@*|node( )" />
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>`.trim() // trim the leading new line
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

// vim:sw=4:ts=4:et:
