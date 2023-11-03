define("ace/snippets/markdown",["require","exports","module"], function(require, exports, module) {
"use strict";

// AT 23/08/23 Changed this file as the default markdown snippets are not useful and geared
// towards github markdown

// $1 / ${1} is numbered tab position (with 1 being first) with no default text.
// ${1:defaultText} is a numbered tab position with default text that is replaced on typing
// $0 is final tab position
// tabTrigger is the bit you type then hit tab to trigger the snippet.

// This file needs to be included in app.html as a <script> tag

exports.snippets = [
    {
        "tabTrigger": "h1",
        "name": "Heading level 1",
        "content": "# ${1:heading}\n\n${0}"
    },
    {
        "tabTrigger": "h2",
        "name": "Heading level 2",
        "content": "## ${1:heading}\n\n${0}"
    },
    {
        "tabTrigger": "h3",
        "name": "Heading level 3",
        "content": "### ${1:heading}\n\n${0}"
    },
    {
        "tabTrigger": "h4",
        "name": "Heading level 4",
        "content": "#### ${1:heading}\n\n${0}"
    },
    {
        "tabTrigger": "h5",
        "name": "Heading level 5",
        "content": "##### ${1:heading}\n\n${0}"
    },
    {
        "tabTrigger": "h6",
        "name": "Heading level 6",
        "content": "###### ${1:heading}\n\n${0}"
    },
    {
        "tabTrigger": "fence",
        "name": "Fenced Block",
        "content": "```${1:language}\n${2}\n```\n${0}"
    },
    {
        "tabTrigger": "xml",
        "name": "Fenced block of XML",
        "content": "```xml\n${1}\n```\n${0}"
    },
    {
        "tabTrigger": "text",
        "name": "Fenced block of plain text",
        "content": "```text\n${1}\n```\n${0}"
    },
    {
        "tabTrigger": "inline",
        "name": "Inline code",
        "content": "`${1:code}`${0}"
    },
    {
        "tabTrigger": "b",
        "name": "Bold text",
        "content": "**${1:bold_text}**${0}"
    },
    {
        "tabTrigger": "i",
        "name": "Italic text",
        "content": "_${1:italic_text}_${0}"
    },
    {
        "tabTrigger": "s",
        "name": "Strike-through text",
        "content": "~~${1:strikethrough_text}~~${0}"
    },
    {
        "tabTrigger": "bi",
        "name": "Bold italic text",
        "content": "***${1:bold_italic_text}***${0}"
    },
];

exports.scope = "markdown";

//exports.snippetText = "# Markdownn
//\n
//# Includes octopress (http://octopress.org/) snippets\n
//\n
//snippet [\n
//	[${1:text}](http://${2:address} \"${3:title}\")\n
//snippet [*\n
//	[${1:link}](${2:`@*`} \"${3:title}\")${4}\n
//\n
//snippet [:\n
//	[${1:id}]: http://${2:url} \"${3:title}\"\n
//snippet [:*\n
//	[${1:id}]: ${2:`@*`} \"${3:title}\"\n
//\n
//snippet ![\n
//	![${1:alttext}](${2:/images/image.jpg} \"${3:title}\")\n
//snippet ![*\n
//	![${1:alt}](${2:`@*`} \"${3:title}\")${4}\n
//\n
//snippet ![:\n
//	![${1:id}]: ${2:url} \"${3:title}\"\n
//snippet ![:*\n
//	![${1:id}]: ${2:`@*`} \"${3:title}\"\n
//\n
//snippet ===\n
//regex /^/=+/=*//\n
//	${PREV_LINE/./=/g}\n
//	\n
//	${0}\n
//snippet ---\n
//regex /^/-+/-*//\n
//	${PREV_LINE/./-/g}\n
//	\n
//	${0}\n
//snippet blockquote\n
//	{% blockquote %}\n
//	${1:quote}\n
//	{% endblockquote %}\n
//\n
//snippet blockquote-author\n
//	{% blockquote ${1:author}, ${2:title} %}\n
//	${3:quote}\n
//	{% endblockquote %}\n
//\n
//snippet blockquote-link\n
//	{% blockquote ${1:author} ${2:URL} ${3:link_text} %}\n
//	${4:quote}\n
//	{% endblockquote %}\n
//\n
//snippet bt-codeblock-short\n
//	```\n
//	${1:code_snippet}\n
//	```\n
//\n
//snippet bt-codeblock-full\n
//	``` ${1:language} ${2:title} ${3:URL} ${4:link_text}\n
//	${5:code_snippet}\n
//	```\n
//\n
//snippet codeblock-short\n
//	{% codeblock %}\n
//	${1:code_snippet}\n
//	{% endcodeblock %}\n
//\n
//snippet codeblock-full\n
//	{% codeblock ${1:title} lang:${2:language} ${3:URL} ${4:link_text} %}\n
//	${5:code_snippet}\n
//	{% endcodeblock %}\n
//\n
//snippet gist-full\n
//	{% gist ${1:gist_id} ${2:filename} %}\n
//\n
//snippet gist-short\n
//	{% gist ${1:gist_id} %}\n
//\n
//snippet img\n
//	{% img ${1:class} ${2:URL} ${3:width} ${4:height} ${5:title_text} ${6:alt_text} %}\n
//\n
//snippet youtube\n
//	{% youtube ${1:video_id} %}\n
//\n
//# The quote should appear only once in the text. It is inherently part of it.\n
//# See http://octopress.org/docs/plugins/pullquote/ for more info.\n
//\n
//snippet pullquote\n
//	{% pullquote %}\n
//	${1:text} {\" ${2:quote} \"} ${3:text}\n
//	{% endpullquote %}\n
//";

});                (function() {
                    window.require(["ace/snippets/markdown"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
