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

exports.snippetText = "# XSLT Snippets\n\
snippet apply-templates with-param\n\
	<xsl:apply-templates select=\"${1:*}\">\n\
		<xsl:with-param name=\"${2:param}\">${3}</xsl:with-param>${4}\n\
	</xsl:apply-templates>${5}\n\
snippet apply-templates sort-by\n\
	<xsl:apply-templates select=\"${1:*}\">\n\
		<xsl:sort select=\"${2:node}\" order=\"${3:ascending}\" data-type=\"${4:text}\">${5}\n\
	</xsl:apply-templates>${6}\n\
snippet apply-templates plain\n\
	<xsl:apply-templates select=\"${1:*}\" />${2}\n\
snippet attribute blank\n\
	<xsl:attribute name=\"${1:name}\">${2}</xsl:attribute>${3}\n\
snippet attribute value-of\n\
	<xsl:attribute name=\"${1:name}\">\n\
		<xsl:value-of select=\"${2:*}\" />\n\
	</xsl:attribute>${3}\n\
snippet call-template\n\
	<xsl:call-template name=\"${1:template}\" />\n\
snippet call-template with-param\n\
	<xsl:call-template name=\"${1:template}\">\n\
		<xsl:with-param name=\"${2:param}\">${3}</xsl:with-param>${4}\n\
	</xsl:call-template>${5}\n\
snippet choose\n\
	<xsl:choose>\n\
		<xsl:when test=\"${1:value}\">\n\
			${2}\n\
		</xsl:when>${3}\n\
	</xsl:choose>\n\
snippet copy-of\n\
	<xsl:copy-of select=\"${1:*}\" />${2}\n\
snippet for-each\n\
	<xsl:for-each select=\"${1:*}\">${2}\n\
	</xsl:for-each>${3}\n\
snippet if\n\
	<xsl:if test=\"${1:test}\">${2}\n\
	</xsl:if>${3}\n\
snippet import\n\
	<xsl:import href=\"${1:stylesheet}\" />${2}\n\
snippet include\n\
	<xsl:include href=\"${1:stylesheet}\" />${2}\n\
snippet otherwise\n\
	<xsl:otherwise>${1}\n\
	</xsl:otherwise>\n\
snippet param\n\
	<xsl:param name=\"${1:name}\">${2}\n\
	</xsl:param>${3}\n\
snippet stylesheet\n\
	<xsl:stylesheet version=\"1.0\"\n\
	xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">${1}\n\
	</xsl:stylesheet>\n\
snippet template\n\
	<xsl:template match=\"${1:*}\">${3}\n\
	</xsl:template>\n\
snippet template named\n\
	<xsl:template name=\"${1:name}\">${2}\n\
	</xsl:template>\n\
snippet text\n\
	<xsl:text>${1}</xsl:text>\n\
snippet value-of\n\
	<xsl:value-of select=\"${1:*}\" />${2}\n\
snippet variable blank\n\
	<xsl:variable name=\"${1:name}\">${2}\n\
	</xsl:variable>\n\
snippet variable select\n\
	<xsl:variable select=\"${1:*}\" />${2}\n\
snippet when\n\
	<xsl:when test=\"${1:test}\">${2}\n\
	</xsl:when>\n\
snippet with-param\n\
	<xsl:with-param name=\"${1:name}\">${2}</xsl:with-param>\n\
snippet with-param select\n\
	<xsl:with-param name=\"${1:name}\" select=\"${2:*}\" />\n\
";
exports.scope = "xml";

});                (function() {
                    window.require(["ace/snippets/xml"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();

// vim:sw=2:ts=2:noet: