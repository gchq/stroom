/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Replaces two of showdown's (2.1.0) sub-parsers whose time grows with the square of a document's size, so a large
// document (e.g. a Documentation doc of hundreds of headings and tables, 500,000 characters) renders in seconds
// rather than tens of seconds. Each gives the same html as showdown's own:
//
// - unhashHTMLSpans put each code span (and inline html span) back with a replace over the whole text, one span at
//   a time; here every span is put back in one pass.
// - hashHTMLBlocks hid each block of html (e.g. each table showdown has made) by searching the whole text again from
//   its start and copying it; here the search carries on from the block before.
//
// Loaded after showdown (see app.html). Showdown looks its sub-parsers up each time it calls one, so these are used
// by every converter.
(function (showdown) {
    'use strict';

    if (!showdown || !showdown.subParser) {
        return;
    }

    // Showdown's marker for a hidden span: "¨C" + its index + "C" (¨ is U+00A8, the diaeresis).
    var SPAN_MARKER = /¨C(\d+)C/;
    var SPAN_MARKERS = /¨C(\d+)C/g;
    // The spans nested in a span put back at most this many times over, as showdown does.
    var NESTING_LIMIT = 10;

    showdown.subParser('unhashHTMLSpans', function (text, options, globals) {
        text = globals.converter._dispatch('unhashHTMLSpans.before', text, options, globals);
        var spans = globals.gHtmlSpans;

        // A span with the spans it holds put back in it, the first marker each time, as showdown does.
        var unhash = function (index) {
            var html = spans[index];
            for (var limit = 0; ; ++limit) {
                var nested = SPAN_MARKER.exec(html);
                if (!nested) {
                    break;
                }
                // A function, so a "$" in the span is not read as a replacement pattern.
                html = html.replace(nested[0], function () {
                    return spans[nested[1]];
                });
                if (limit === NESTING_LIMIT) {
                    console.error('maximum nesting of 10 spans reached!!!');
                    break;
                }
            }
            return html;
        };

        text = text.replace(SPAN_MARKERS, function (marker, index) {
            return Number(index) < spans.length
                ? unhash(index)
                : marker;
        });

        text = globals.converter._dispatch('unhashHTMLSpans.after', text, options, globals);
        return text;
    });

    var BLOCK_TAGS = [
        'pre', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote', 'table', 'dl', 'ol', 'ul', 'script',
        'noscript', 'form', 'fieldset', 'iframe', 'math', 'style', 'section', 'header', 'footer', 'nav', 'article',
        'aside', 'address', 'audio', 'canvas', 'figure', 'hgroup', 'output', 'video', 'p'
    ];

    // The first element in the text from the given index, from its opening tag to its closing tag, its own kind
    // nested in it balanced; or null for none. As showdown's rgxFindMatchPos for a first match only: an opening tag
    // never closed is passed over, the search starting again after it.
    var findElement = function (text, from, left, right) {
        var tags = new RegExp(left + '|' + right, 'gim');
        var opening = new RegExp(left, 'im');
        var depth;
        var start;
        var contentStart;
        tags.lastIndex = from;
        do {
            depth = 0;
            var tag;
            while ((tag = tags.exec(text))) {
                if (opening.test(tag[0])) {
                    if (!(depth++)) {
                        contentStart = tags.lastIndex;
                        start = contentStart - tag[0].length;
                    }
                } else if (depth && !--depth) {
                    return {
                        start: start,
                        contentStart: contentStart,
                        contentEnd: tag.index,
                        end: tag.index + tag[0].length
                    };
                }
            }
        } while (depth && (tags.lastIndex = contentStart));
        return null;
    };

    showdown.subParser('hashHTMLBlocks', function (text, options, globals) {
        text = globals.converter._dispatch('hashHTMLBlocks.before', text, options, globals);

        var hash = function (wholeMatch, match, left, right) {
            var txt = wholeMatch;
            // An html element marked as markdown has its content read as markdown.
            if (left.search(/\bmarkdown\b/) !== -1) {
                txt = left + globals.converter.makeHtml(match) + right;
            }
            return '\n\n¨K' + (globals.gHtmlBlocks.push(txt) - 1) + 'K\n\n';
        };

        if (options.backslashEscapesHTMLTags) {
            // Backslash escaped html tags as text.
            text = text.replace(/\\<(\/?[^>]+?)>/g, function (wm, inside) {
                return '&lt;' + inside + '&gt;';
            });
        }

        BLOCK_TAGS.forEach(function (blockTag) {
            var lineStart = new RegExp('^ {0,3}(<' + blockTag + '\\b[^>]*>)', 'im');
            var left = '<' + blockTag + '\\b[^>]*>';
            var right = '</' + blockTag + '>';
            // The text before the rest, done with: no element of this kind starts a line in it.
            var done = [];
            var rest = text;
            var line;
            while ((line = lineStart.exec(rest))) {
                // Where the opening tag starts, after the line's indent.
                var opensAt = line.index + line[0].length - line[1].length;
                var element = findElement(rest, opensAt, left, right);
                if (!element) {
                    // Showdown stops at an element never closed, leaving any after it.
                    break;
                }
                var hidden = hash(
                    rest.slice(element.start, element.end),
                    rest.slice(element.contentStart, element.contentEnd),
                    rest.slice(element.start, element.contentStart),
                    rest.slice(element.contentEnd, element.end));
                if (element.start === opensAt) {
                    // The element starting the line hidden: the search carries on after it (its marker ends a
                    // line, so the rest starts one).
                    done.push(rest.slice(0, element.start), hidden);
                    rest = rest.slice(element.end);
                } else {
                    // One nested in it hidden instead (it was never closed): searched again from the line, as
                    // showdown does.
                    rest = rest.slice(0, element.start) + hidden + rest.slice(element.end);
                }
            }
            done.push(rest);
            text = done.join('');
        });

        // An hr on its own.
        text = text.replace(/(\n {0,3}(<(hr)\b([^<>])*?\/?>)[ \t]*(?=\n{2,}))/g,
            showdown.subParser('hashElement')(text, options, globals));

        // An html comment on its own.
        text = showdown.helper.replaceRecursiveRegExp(text, function (txt) {
            return '\n\n¨K' + (globals.gHtmlBlocks.push(txt) - 1) + 'K\n\n';
        }, '^ {0,3}<!--', '-->', 'gm');

        // PHP and ASP style processing instructions (<?...?> and <%...%>).
        text = text.replace(/(?:\n\n)( {0,3}(?:<([?%])[^\r]*?\2>)[ \t]*(?=\n{2,}))/g,
            showdown.subParser('hashElement')(text, options, globals));

        text = globals.converter._dispatch('hashHTMLBlocks.after', text, options, globals);
        return text;
    });
}(window.showdown));
