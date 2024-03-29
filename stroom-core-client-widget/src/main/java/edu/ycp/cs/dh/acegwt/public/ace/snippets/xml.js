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
        "name": "Apply-templates with-param",
        "content": `
<xsl:apply-templates select="\${1:*}">
  <xsl:with-param name="\${2:param}">\${3}</xsl:with-param>
  \${0}
</xsl:apply-templates>`.trim()
    },

    {
        "tabTrigger": "applysort",
        "name": "Apply-templates sort-by",
        "content": `
<xsl:apply-templates select="\${1:*}">
  <xsl:sort select="\${2:node}" order="\${3:ascending}" data-type="\${4:text}">\${5}
</xsl:apply-templates>
\${0}`.trim()
    },

    {
        "tabTrigger": "apply",
        "name": "Apply-templates plain",
        "content": `
<xsl:apply-templates select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "attr",
        "name": "Attribute blank",
        "content": `
<xsl:attribute name="\${1:name}">\${2}</xsl:attribute>
\${0}`.trim()
    },

    {
        "tabTrigger": "attrval",
        "name": "Attribute value-of",
        "content": `
<xsl:attribute name="\${1:name}">
  <xsl:value-of select="\${2:*}" />
</xsl:attribute>
\${0}`.trim()
    },

    {
        "tabTrigger": "call",
        "name": "Call-template",
        "content": `
<xsl:call-template name="\${1:template}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "wcall",
        "name": "Call-template with-param",
        "content": `
<xsl:call-template name="\${1:template}">
  <xsl:with-param name="\${2:param}">\${3}</xsl:with-param>\${4}
</xsl:call-template>
\${0}`.trim()
    },

    {
        "tabTrigger": "choose",
        "name": "Choose",
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
        "name": "Copy-of",
        "content": `
<xsl:copy-of select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "elem",
        "name": "Element blank",
        "content": `
<xsl:element name="\${1:name}">
  \${2}
</xsl:element>
\${0}`.trim()
    },

    {
        "tabTrigger": "foreach",
        "name": "For-each",
        "content": `
<xsl:for-each select="\${1:*}">
  \${2}
</xsl:for-each>
\${0}`.trim()
    },

    {
        "tabTrigger": "if",
        "name": "If",
        "content": `
<xsl:if test="\${1:test}">
  \${2}
</xsl:if>
\${0}`.trim()
    },

    {
        "tabTrigger": "imp",
        "name": "Import",
        "content": `
<xsl:import href="\${1:stylesheet}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "inc",
        "name": "Include",
        "content": `
<xsl:include href="\${1:stylesheet}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "otherwise",
        "name": "Otherwise",
        "content": `
<xsl:otherwise>
  \${1}
</xsl:otherwise>
\$0`.trim()
    },

    {
        "tabTrigger": "param",
        "name": "Param",
        "content": `
<xsl:param name="\${1:name}">
  \${2}
</xsl:param>
\${0}`.trim()
    },

    {
        "tabTrigger": "style",
        "name": "Stylesheet",
        "content": `
<xsl:stylesheet
    version="1.0"
    xmlns="\${1}"
    xpath-default-namespace="\${2:\${1}}"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  \${0}
</xsl:stylesheet>`.trim()
    },

    {
        "tabTrigger": "temp",
        "name": "Template",
        "content": `
<xsl:template match="\${1:*}">
  \${2}
</xsl:template>
\$0`.trim()
    },

    {
        "tabTrigger": "ntemp",
        "name": "Template named",
        "content": `
<xsl:template name="\${1:name}">
  \${2}
</xsl:template>
\$0`.trim()
    },

    {
        "tabTrigger": "text",
        "name": "Text",
        "content": `
<xsl:text>\${1}</xsl:text>
\$0`.trim()
    },

    {
        "tabTrigger": "valof",
        "name": "Value-of",
        "content": `
<xsl:value-of select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "var",
        "name": "Variable blank",
        "content": `
<xsl:variable name="\${1:name}">
  \${0}
</xsl:variable>`.trim()
    },

    {
        "tabTrigger": "varsel",
        "name": "Variable select",
        "content": `
<xsl:variable select="\${1:*}" />
\${0}`.trim()
    },

    {
        "tabTrigger": "when",
        "name": "When",
        "content": `
<xsl:when test="\${1:test}">
  \${0}
</xsl:when>`.trim()
    },

    {
        "tabTrigger": "wparam",
        "name": "With-param",
        "content": `
<xsl:with-param name="\${1:name}">\${2}</xsl:with-param>
\${0}`.trim()
    },

    {
        "tabTrigger": "wparamsel",
        "name": "With-param select",
        "content": `
<xsl:with-param name="\${1:name}" select="\${2:*}" />
\${0}`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "fatal",
        "name": "Fatal message",
        "content": `
<xsl:message terminate="yes">\${1}</xsl:message>
\${0}`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "error",
        "name": "Error message",
        "content": `
<xsl:message><error>\${1}</error></xsl:message>
\${0}`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "warn",
        "name": "Warning message",
        "content": `
<xsl:message><warn>\${1}</warn></xsl:message>
\${0}`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "info",
        "name": "Info message",
        "content": `
<xsl:message><info>\${1}</info></xsl:message>
\${0}`.trim() // trim the leading new line
    },

    {
        "tabTrigger": "ident",
        "name": "Identity skeleton",
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
        "name": "Records identity skeleton",
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
        "name": "Events identity skeleton",
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
