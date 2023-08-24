define("ace/snippets/markdown",["require","exports","module"], function(require, exports, module) {
"use strict";


// AT 23/08/23 Commented the default snippets out as the are for github markdown I think, so have crafted my
// own
exports.snippetText = "# Markdown\n\
snippet h1\n\
\t# ${1:heading}\n\
\t\n\
\t${0}\n\
snippet h2\n\
\t## ${1:heading}\n\
\t\n\
\t${0}\n\
snippet h3\n\
\t### ${1:heading}\n\
\t\n\
\t${0}\n\
snippet h4\n\
\t#### ${1:heading}\n\
\t\n\
\t${0}\n\
snippet h5\n\
\t##### ${1:heading}\n\
\t\n\
\t${0}\n\
snippet fence\n\
\t```${1:language}\n\
\t${2}\n\
\t```${0}\n\
snippet xml\n\
\t```xml\n\
\t${2}\n\
\t```${0}\n\
snippet text\n\
\t```text\n\
\t${2}\n\
\t```${0}\n\
snippet inline\n\
\t`${1:code}`${0}\n\
snippet b\n\
\t**${1:bold_text}**${0}\n\
snippet i\n\
\t_${1:italic_text}_${0}\n\
snippet s\n\
\t~~${1:strikethrough_text}~~${0}\n\
snippet bold-italic\n\
\t***${1:bold_italic_text}***${0}\n\
snippet link\n\
\t[${1:title}](${2:url})${0}\n\
";

//exports.snippetText = "# Markdown\n\
//\n\
//# Includes octopress (http://octopress.org/) snippets\n\
//\n\
//snippet [\n\
//	[${1:text}](http://${2:address} \"${3:title}\")\n\
//snippet [*\n\
//	[${1:link}](${2:`@*`} \"${3:title}\")${4}\n\
//\n\
//snippet [:\n\
//	[${1:id}]: http://${2:url} \"${3:title}\"\n\
//snippet [:*\n\
//	[${1:id}]: ${2:`@*`} \"${3:title}\"\n\
//\n\
//snippet ![\n\
//	![${1:alttext}](${2:/images/image.jpg} \"${3:title}\")\n\
//snippet ![*\n\
//	![${1:alt}](${2:`@*`} \"${3:title}\")${4}\n\
//\n\
//snippet ![:\n\
//	![${1:id}]: ${2:url} \"${3:title}\"\n\
//snippet ![:*\n\
//	![${1:id}]: ${2:`@*`} \"${3:title}\"\n\
//\n\
//snippet ===\n\
//regex /^/=+/=*//\n\
//	${PREV_LINE/./=/g}\n\
//	\n\
//	${0}\n\
//snippet ---\n\
//regex /^/-+/-*//\n\
//	${PREV_LINE/./-/g}\n\
//	\n\
//	${0}\n\
//snippet blockquote\n\
//	{% blockquote %}\n\
//	${1:quote}\n\
//	{% endblockquote %}\n\
//\n\
//snippet blockquote-author\n\
//	{% blockquote ${1:author}, ${2:title} %}\n\
//	${3:quote}\n\
//	{% endblockquote %}\n\
//\n\
//snippet blockquote-link\n\
//	{% blockquote ${1:author} ${2:URL} ${3:link_text} %}\n\
//	${4:quote}\n\
//	{% endblockquote %}\n\
//\n\
//snippet bt-codeblock-short\n\
//	```\n\
//	${1:code_snippet}\n\
//	```\n\
//\n\
//snippet bt-codeblock-full\n\
//	``` ${1:language} ${2:title} ${3:URL} ${4:link_text}\n\
//	${5:code_snippet}\n\
//	```\n\
//\n\
//snippet codeblock-short\n\
//	{% codeblock %}\n\
//	${1:code_snippet}\n\
//	{% endcodeblock %}\n\
//\n\
//snippet codeblock-full\n\
//	{% codeblock ${1:title} lang:${2:language} ${3:URL} ${4:link_text} %}\n\
//	${5:code_snippet}\n\
//	{% endcodeblock %}\n\
//\n\
//snippet gist-full\n\
//	{% gist ${1:gist_id} ${2:filename} %}\n\
//\n\
//snippet gist-short\n\
//	{% gist ${1:gist_id} %}\n\
//\n\
//snippet img\n\
//	{% img ${1:class} ${2:URL} ${3:width} ${4:height} ${5:title_text} ${6:alt_text} %}\n\
//\n\
//snippet youtube\n\
//	{% youtube ${1:video_id} %}\n\
//\n\
//# The quote should appear only once in the text. It is inherently part of it.\n\
//# See http://octopress.org/docs/plugins/pullquote/ for more info.\n\
//\n\
//snippet pullquote\n\
//	{% pullquote %}\n\
//	${1:text} {\" ${2:quote} \"} ${3:text}\n\
//	{% endpullquote %}\n\
//";
exports.scope = "markdown";

});                (function() {
                    window.require(["ace/snippets/markdown"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
