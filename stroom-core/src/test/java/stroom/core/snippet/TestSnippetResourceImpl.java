package stroom.core.snippet;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TestSnippetResourceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSnippetResourceImpl.class);


    private static final String SNIPPET_PATTERN = "^#.*|^(\\{[\\s\\S]*})\\s*$|^(\\S+) (.*)$|^((?:\\n*\\t.*)+)";

    @Test
    void name() {

        final String str = "snippet value-of\n" +
                "\t<xsl:value-of select=\"${1:*}\" />${2}\n" +
                "\n" +
                "snippet variable blank\n" +
                "\t<xsl:variable name=\"${1:name}\">${2}\n" +
                "\t</xsl:variable>\n" +
                "\n" +
                "\n" +
                "snippet variable select\n" +
                "\t<xsl:variable select=\"${1:*}\" />${2}";

        parseSnippets(str);
    }


    private List<Snippet> parseSnippets(final String snippetsStr) {

        // see:
        // https://cloud9-sdk.readme.io/docs/snippets
        // https://github.com/ajaxorg/ace/blob/master/lib/ace/snippets.js
        // https://github.com/ajaxorg/ace/blob/master/lib/ace/ext/language_tools.js - var snippetCompleter
        // AceEditor.addCompletionProvider

        if (snippetsStr == null || snippetsStr.isEmpty()) {
            return Collections.emptyList();
        } else {

            final Pattern pattern = Pattern.compile(SNIPPET_PATTERN, Pattern.MULTILINE);

            final Matcher matcher = pattern.matcher(snippetsStr);

            final List<Snippet> snippets = new ArrayList<>();
            String caption = null;
            String snippet = null;

            while (matcher.find()) {
                if (matcher.group(4) != null) {
                    snippet = matcher.group(4).replaceAll("\\t", "");

                    snippets.add(new Snippet(caption, snippet));
                } else {
                    final String key = matcher.group(2);
                    final String val = matcher.group(3);

                    if ("snippet".equals(key)) {



                    } else {
                        throw new RuntimeException("Unknown key " + key);
                    }
                }

                LOGGER.info("\n1:\n{}\n2:\n{}\n3:\n{}\n4:\n{}",
                        matcher.group(1),
                        matcher.group(2),
                        matcher.group(3),
                        matcher.group(4));
            }
            return Collections.emptyList();
        }
    }
}