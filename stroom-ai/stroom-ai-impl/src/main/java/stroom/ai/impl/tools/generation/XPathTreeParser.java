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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.PyText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Parses the XPath expressions of one stylesheet, with its namespace bindings, into trees of [XPathNode]: the
/// Stroom MCP server's `xpathtree.XPathParser`, which parses with elementpath (a strict XPath 3.1 parser). This
/// parser writes the trees elementpath writes, node for node (the same symbols, values and children), and gives each
/// node the text elementpath's token positions give it, so an expression read back is written as it was.
///
/// Parsing only, nothing is evaluated. Stroom's functions (`stroom:`), the XSLT's own (`mcp:`, or a prefix added by
/// hand) and an imported XSLT's are functions of any arity, as long as the stylesheet binds their prefix; a prefix
/// it doesn't bind is an error (XPST0081), as it is in Stroom; so is a standard function it doesn't know, or one
/// called with the wrong number of arguments (XPST0017).
///
/// Unlike elementpath, constant parts of an expression are not evaluated as it is parsed (elementpath refuses
/// `xs:integer('abc')` or `1 div 0` as it parses them), and inline functions (`function($x) {...}`) and URI-qualified
/// names (`Q{uri}name`) are not read: they are errors here.
public final class XPathTreeParser {

    // elementpath's tokenizer for XPath 3.1, as it builds it: literals, symbols (operators, keywords, axis and
    // function names, judged by what follows them), names, anything else.
    private static final Pattern TOKENS = Pattern.compile(
            "('(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"|(?:\\d+|\\.\\d+)(?:\\.\\d*)?(?:[Ee][+-]?\\d+)?)"
            + "|(\\.\\.|\\|\\||\\(:|:\\)|::|!=|<=|>=|//|<<|>>|:=|=>"
            + "|[\\)\\]:\\$\\*\\.=<>\\+\\-\\|/\\[@,\\?\\{\\}\\(!\\#]"
            + "|\\b(?:for\\-each\\-pair|insert\\-before|fold\\-right|fold\\-left|satisfies|intersect|for\\-each"
            + "|instance|castable|contains|subarray|reverse|flatten|return|except|remove|append|filter|union|every"
            + "|treat|exp10|log10|atan2|entry|merge|then|else|some|cast|idiv|sqrt|asin|acos|atan|size|keys|find"
            + "|head|tail|join|sort|and|div|mod|for|let|exp|log|pow|sin|cos|tan|get|put|or|as|of|in|if|eq|ne|lt"
            + "|gt|le|ge|is|to|pi)\\b(?![\\-\\.])"
            + "|(?<!\\$)\\b[^\\d\\W][\\w.\\-\\xb7\\u0300-\\u036F\\u203F\\u2040]*"
            + "(?=\\s*(?:\\(\\:.*\\:\\))?\\s*\\((?!\\:))"
            + "|\\battribute(?=\\s*\\:\\:|\\s*\\(\\:.*\\:\\)\\s*\\:\\:|\\s*\\(|\\s*\\(\\:.*\\:\\)\\()"
            + "|(?<!\\$)\\barray(?=\\s*(?:\\(\\:.*\\:\\))?\\s*(?=\\(|\\{)(?!\\:))"
            + "|(?<!\\$)\\bmap(?=\\s*(?:\\(\\:.*\\:\\))?\\s*(?=\\(|\\{)(?!\\:))"
            + "|Q\\{"
            + "|\\b[^\\d\\W][\\w.\\-\\xb7\\u0300-\\u036F\\u203F\\u2040]*(?=\\s*\\:\\:|\\s*\\(\\:.*\\:\\)\\s*\\:\\:))"
            + "|([^\\d\\W][\\w.\\-\\xb7\\u0300-\\u036F\\u203F\\u2040]*)"
            + "|(\\S)"
            + "|\\s+",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WORD = Pattern.compile("[^\\d\\W][\\w.\\-\\xb7\\u0300-\\u036F\\u203F\\u2040]*",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final String OPEN = "([{";
    private static final String CLOSE = ")]}";
    private static final Set<String> COMPARISONS = Set.of("=", "!=", "<", "<=", ">", ">=", "eq", "ne", "lt",
            "le", "gt", "ge", "is", "<<", ">>");
    private static final Map<String, Integer> BINDING = bindingPowers();
    private static final Set<String> STEP_SYMBOLS = Set.of("*", "@", "..", ".", "(", "{", "Q{", "$");
    // The tokens a path step may be, by symbol (elementpath's PATH_STEP_SYMBOLS).
    private static final Set<String> STEP_TOKENS = Set.of("(integer)", "(string)", "(float)", "(decimal)", "(name)",
            "*", "@", "..", ".", "(", "{", "Q{", "$");
    /// The most parsers kept, one per set of bindings: the least recently used are dropped first (the bindings
    /// come from the XSLTs and mappings the agent sends, so there is no end to them).
    static final int MAX_PARSERS = 64;
    private static final Map<Map<String, String>, XPathTreeParser> PARSERS = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<Map<String, String>, XPathTreeParser> eldest) {
                    return size() > MAX_PARSERS;
                }
            });

    private final Map<String, String> namespaces;

    /// @param namespaces The stylesheet's namespace bindings, prefix to URI; a null or empty prefix (the default
    ///                   namespace) is left out.
    public XPathTreeParser(final Map<String, String> namespaces) {
        final Map<String, String> own = new TreeMap<>();
        namespaces.forEach((prefix, uri) -> {
            if (prefix != null && !prefix.isEmpty()) {
                own.put(prefix, uri);
            }
        });
        this.namespaces = Map.copyOf(own);
    }

    /// @param namespaces The stylesheet's namespace bindings.
    /// @return A parser for them (one per set of bindings, as the Python server caches them, up to [#MAX_PARSERS]).
    public static XPathTreeParser parserFor(final Map<String, String> namespaces) {
        final Map<String, String> own = new TreeMap<>();
        namespaces.forEach((prefix, uri) -> {
            if (prefix != null && !prefix.isEmpty()) {
                own.put(prefix, uri);
            }
        });
        return PARSERS.computeIfAbsent(Map.copyOf(own), XPathTreeParser::new);
    }

    /// @return How many parsers are kept, for tests.
    static int parsersKept() {
        return PARSERS.size();
    }

    /// @return The namespace bindings it parses with (no default namespace).
    public Map<String, String> namespaces() {
        return namespaces;
    }

    /// @param text An XPath expression.
    /// @return Its tree.
    /// @throws XPathParseException if it isn't one this parser reads.
    public XPathNode parse(final String text) {
        final List<Lexeme> lexemes = lexemes(text);
        final Run run = new Run(text, lexemes);
        if (run.lexemes.isEmpty()) {
            throw new XPathParseException("source is empty (XPST0003)");
        }
        final Token token = run.expression(0);
        if (run.at < run.lexemes.size()) {
            throw run.unexpected();
        }
        return node(token, text, lexemes);
    }

    // --- the lexemes ----------------------------------------------------------------------------------------------

    private static List<Lexeme> lexemes(final String text) {
        final List<Lexeme> out = new ArrayList<>();
        final Matcher matcher = TOKENS.matcher(text);
        while (matcher.find()) {
            final String group = matcher.group();
            if (group.isBlank()) {
                continue;
            }
            final Kind kind;
            if (matcher.group(1) != null) {
                kind = Kind.LITERAL;
            } else if (matcher.group(2) != null) {
                kind = Kind.SYMBOL;
            } else if (matcher.group(3) != null) {
                kind = Kind.NAME;
            } else {
                kind = Kind.UNKNOWN;
            }
            out.add(new Lexeme(matcher.start(), matcher.end(), group, kind));
        }
        return out;
    }

    // --- tokens to nodes, as xpathtree's _node and _span make them ------------------------------------------------

    private static XPathNode node(final Token token, final String text, final List<Lexeme> lexemes) {
        final String symbol;
        final Object value;
        final List<Token> children;
        if (token.kind == TokenKind.MAP) {
            symbol = "map";
            value = null;
            children = token.children;
        } else if (isPrefixedCall(token)) {
            symbol = "call";
            value = token.value;
            children = token.children.get(1).children;
        } else if (token.kind == TokenKind.FUNCTION) {
            symbol = "call";
            value = token.symbol;
            children = token.children;
        } else {
            symbol = token.symbol;
            value = token.value;
            children = token.children;
        }
        final List<XPathNode> nodes = new ArrayList<>(children.size());
        for (final Token child : children) {
            nodes.add(node(child, text, lexemes));
        }
        final int[] span = span(token, text, lexemes);
        return new XPathNode(symbol, value, nodes, text.substring(span[0], span[1]), span[0], span[1]);
    }

    private static boolean isPrefixedCall(final Token token) {
        return ":".equals(token.symbol) && token.children.size() == 2
               && token.children.get(1).kind == TokenKind.FUNCTION;
    }

    // The token's whole expression in the text: its tokens' extent, with the brackets the tree leaves out.
    private static int[] span(final Token token, final String text, final List<Lexeme> lexemes) {
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        for (final Token each : token.subtree()) {
            start = Math.min(start, each.start);
            end = Math.max(end, each.end);
        }
        final Lexeme before = last(lexemes, start);
        if (token.kind == TokenKind.MAP && before != null
            && ("map".equals(before.text) || "array".equals(before.text))) {
            start = before.start;
        } else if (token.kind == TokenKind.FUNCTION && before != null && before.text.equals(token.symbol)
                   && text.startsWith("(", start)) {
            start = before.start;
        }
        int depth = 0;
        final List<Lexeme> after = new ArrayList<>();
        for (final Lexeme lexeme : lexemes) {
            if (lexeme.start >= start && lexeme.start < end) {
                depth += lexeme.depthChange();
            } else if (lexeme.start >= end) {
                after.add(lexeme);
            }
        }
        final boolean isCall = token.kind == TokenKind.FUNCTION || isPrefixedCall(token);
        if (depth == 0 && isCall && token.kind != TokenKind.MAP && !after.isEmpty()
            && "(".equals(after.getFirst().text)) {
            // A call with no arguments: name() taken whole.
            for (final Lexeme lexeme : after) {
                depth += lexeme.depthChange();
                end = lexeme.end;
                if (depth == 0) {
                    break;
                }
            }
            return new int[]{start, end};
        }
        for (final Lexeme lexeme : after) {
            if (depth <= 0) {
                break;
            }
            depth += lexeme.depthChange();
            end = lexeme.end;
        }
        return new int[]{start, end};
    }

    private static Lexeme last(final List<Lexeme> lexemes, final int before) {
        Lexeme found = null;
        for (final Lexeme lexeme : lexemes) {
            if (lexeme.end <= before) {
                found = lexeme;
            }
        }
        return found;
    }

    private static Map<String, Integer> bindingPowers() {
        final Map<String, Integer> out = new HashMap<>();
        out.put(",", 5);
        out.put("or", 20);
        out.put("and", 25);
        COMPARISONS.forEach(symbol -> out.put(symbol, 30));
        out.put("||", 32);
        out.put("to", 35);
        out.put("+", 40);
        out.put("-", 40);
        out.put("*", 45);
        out.put("div", 45);
        out.put("idiv", 45);
        out.put("mod", 45);
        out.put("|", 50);
        out.put("union", 50);
        out.put("intersect", 55);
        out.put("except", 55);
        out.put("instance", 60);
        out.put("treat", 61);
        out.put("castable", 62);
        out.put("cast", 63);
        out.put("=>", 67);
        out.put("!", 72);
        out.put("/", 75);
        out.put("//", 75);
        out.put("[", 80);
        out.put("(", 80);
        out.put("?", 85);
        out.put("#", 90);
        out.put(":", 95);
        return Map.copyOf(out);
    }


    // --------------------------------------------------------------------------------


    /// One parse: the text's lexemes and where the parser is in them.
    private final class Run {

        private final String text;
        private final List<Lexeme> lexemes = new ArrayList<>();
        private int at;
        // Whether the next nud is a path step: a keyword there is a name (a/and, a/if).
        private boolean step;

        private Run(final String text, final List<Lexeme> all) {
            this.text = text;
            // Comments are skipped, as elementpath skips them; their lexemes still count in a node's text.
            boolean comment = false;
            for (final Lexeme lexeme : all) {
                if (comment) {
                    comment = !":)".equals(lexeme.text);
                } else if ("(:".equals(lexeme.text)) {
                    comment = true;
                } else {
                    lexemes.add(lexeme);
                }
            }
            if (comment) {
                throw new XPathParseException("unterminated comment (XPST0003)");
            }
        }

        // Pratt's expression: the next token's nud, then each following token's led while it binds tighter.
        private Token expression(final int rbp) {
            final Lexeme first = advance();
            Token left = nud(first);
            while (rbp < leftBindingPower(left)) {
                left = led(advance(), left);
            }
            return left;
        }

        private int leftBindingPower(final Token left) {
            final Lexeme next = peek();
            if (next == null || next.kind != Kind.SYMBOL) {
                return 0;
            }
            if (":".equals(next.text) && !prefixes(left, next)) {
                return 0;
            }
            return BINDING.getOrDefault(next.text, 0);
        }

        // A ':' joins a prefix and a name only when it touches both and follows a name or *: elsewhere (a map's
        // key and value) it ends the expression.
        private boolean prefixes(final Token left, final Lexeme colon) {
            final Lexeme after = peek(1);
            return ("(name)".equals(left.symbol) || "*".equals(left.symbol)) && left.children.isEmpty()
                   && left.end == colon.start && after != null && after.start == colon.end;
        }

        private Token nud(final Lexeme lexeme) {
            final boolean isStep = step;
            step = false;
            if (isStep && lexeme.kind != Kind.LITERAL && WORD.matcher(lexeme.text).matches()) {
                return stepWord(lexeme);
            }
            if (lexeme.kind == Kind.LITERAL) {
                return literal(lexeme);
            }
            if (lexeme.kind == Kind.UNKNOWN) {
                throw unexpected(lexeme);
            }
            return switch (lexeme.text) {
                case "(" -> parenthesised(lexeme);
                case "[" -> squareArray(lexeme);
                case "@" -> withChild(token("@", lexeme), expression(80));
                case "$" -> variable(lexeme);
                case ".", "..", "*" -> token(lexeme.text, lexeme);
                case "/" -> root(lexeme);
                case "//" -> descendants(lexeme);
                case "-", "+" -> withChild(token(lexeme.text, lexeme), expression(70));
                case "?" -> withChild(token("?", lexeme), lookupKey());
                case "Q{" -> throw new XPathParseException("URI-qualified names are not read here");
                default -> word(lexeme);
            };
        }

        // A word as a path step: an axis, a call or a kind test, or else a name, whatever keyword it is.
        private Token stepWord(final Lexeme lexeme) {
            final Lexeme next = peek();
            if (lexeme.kind == Kind.SYMBOL && next != null && "::".equals(next.text)) {
                return axis(lexeme);
            }
            if (lexeme.kind == Kind.SYMBOL && next != null && "(".equals(next.text)) {
                if ("if".equals(lexeme.text)) {
                    throw new XPathParseException("'if' is not allowed as function name (XPST0003)");
                }
                return call(lexeme, null);
            }
            return name(lexeme);
        }

        private Token word(final Lexeme lexeme) {
            if (!WORD.matcher(lexeme.text).matches()) {
                throw unexpected(lexeme);
            }
            final Lexeme next = peek();
            final String name = lexeme.text;
            if ("if".equals(name) && isNext("(")) {
                return conditional(lexeme);
            }
            if (Set.of("for", "some", "every", "let").contains(name) && isNext("$")) {
                return binding(lexeme);
            }
            if (("map".equals(name) || "array".equals(name)) && isNext("{")) {
                return "map".equals(name)
                        ? mapConstructor()
                        : curlyArray();
            }
            // Axis and function names are told apart from other names as they are lexed, by what follows them (a
            // name after $ never is one).
            if (lexeme.kind == Kind.SYMBOL && next != null && "::".equals(next.text)) {
                return axis(lexeme);
            }
            if (lexeme.kind == Kind.SYMBOL && next != null && "(".equals(next.text)) {
                return call(lexeme, null);
            }
            return name(lexeme);
        }

        private Token literal(final Lexeme lexeme) {
            final String text = lexeme.text;
            final char quote = text.charAt(0);
            if (quote == '\'' || quote == '"') {
                final String body = text.substring(1, text.length() - 1);
                final String doubled = String.valueOf(quote) + quote;
                return new Token("(string)", body.replace(doubled, String.valueOf(quote)), lexeme);
            }
            if (text.contains("e") || text.contains("E")) {
                return new Token("(float)", Double.valueOf(text), lexeme);
            }
            if (text.contains(".")) {
                return new Token("(decimal)", new BigDecimal(text.endsWith(".")
                        ? text.substring(0, text.length() - 1)
                        : text), lexeme);
            }
            return new Token("(integer)", new BigInteger(text), lexeme);
        }

        private Token parenthesised(final Lexeme lexeme) {
            final Token paren = token("(", lexeme);
            if (!isNext(")")) {
                paren.children.add(expression(0));
            }
            expect(")");
            return paren;
        }

        // [a, b]: elementpath's square array, a function named [ placed at its first member.
        private Token squareArray(final Lexeme lexeme) {
            final Lexeme first = peek();
            final int at = first == null
                    ? lexeme.end
                    : first.start;
            final Token array = new Token("[", "[", at, at, TokenKind.FUNCTION);
            if (!isNext("]")) {
                array.children.add(expression(5));
                while (isNext(",")) {
                    advance();
                    array.children.add(expression(5));
                }
            }
            expect("]");
            return array;
        }

        private Token curlyArray() {
            final Lexeme brace = expect("{");
            final Token array = new Token("array", "array", brace.start, brace.end, TokenKind.FUNCTION);
            if (!isNext("}")) {
                array.children.add(expression(0));
            }
            expect("}");
            return array;
        }

        private Token mapConstructor() {
            final Lexeme brace = expect("{");
            final Token map = new Token("map", "map", brace.start, brace.end, TokenKind.MAP);
            if (!isNext("}")) {
                while (true) {
                    map.children.add(expression(5));
                    expect(":");
                    map.children.add(expression(5));
                    if (!isNext(",")) {
                        break;
                    }
                    advance();
                }
            }
            expect("}");
            return map;
        }

        private Token variable(final Lexeme lexeme) {
            final Lexeme next = peek();
            if (next == null || !WORD.matcher(next.text).matches()) {
                throw unexpected(next);
            }
            final Token name = expression(90);
            if (!"(name)".equals(name.symbol) && !":".equals(name.symbol)) {
                throw unexpected(lexeme);
            }
            final Token variable = new Token("$", name.value, lexeme);
            variable.children.add(name);
            return variable;
        }

        private Token root(final Lexeme lexeme) {
            final Token root = token("/", lexeme);
            if (startsStep(peek())) {
                step = true;
                root.children.add(expression(75));
            }
            return root;
        }

        private Token descendants(final Lexeme lexeme) {
            if (!startsStep(peek())) {
                throw unexpected(peek());
            }
            step = true;
            return withChild(token("//", lexeme), expression(75));
        }

        private boolean startsStep(final Lexeme next) {
            if (next == null) {
                return false;
            }
            if (next.kind == Kind.LITERAL || next.kind == Kind.NAME || STEP_SYMBOLS.contains(next.text)) {
                return true;
            }
            // Any word: a keyword there is a name, or a call.
            return WORD.matcher(next.text).matches();
        }

        private Token conditional(final Lexeme lexeme) {
            final Token conditional = token("if", lexeme);
            expect("(");
            conditional.children.add(expression(5));
            expect(")");
            expect("then");
            conditional.children.add(expression(5));
            expect("else");
            conditional.children.add(expression(5));
            return conditional;
        }

        // for, let, some and every: each variable and what it ranges over (or is bound to), then what is returned.
        private Token binding(final Lexeme lexeme) {
            final Token binding = token(lexeme.text, lexeme);
            final boolean let = "let".equals(lexeme.text);
            while (true) {
                if (!isNext("$")) {
                    throw unexpected(peek());
                }
                final Token variable = expression(5);
                binding.children.add(variable);
                expect(let
                        ? ":="
                        : "in");
                final Token range = expression(5);
                binding.children.add(range);
                if (!let) {
                    for (final Token each : range.subtree()) {
                        if ("$".equals(each.symbol) && Objects.equals(each.value, variable.value)) {
                            throw new XPathParseException("loop variable in its range expression (XPST0008)");
                        }
                    }
                }
                if (!isNext(",")) {
                    break;
                }
                advance();
            }
            expect(switch (lexeme.text) {
                case "some", "every" -> "satisfies";
                default -> "return";
            });
            binding.children.add(expression(5));
            return binding;
        }

        private Token axis(final Lexeme lexeme) {
            if (!XPathFunctions.AXES.contains(lexeme.text)) {
                throw new XPathParseException("unknown axis " + lexeme.text + " (XPST0003)");
            }
            final Token axis = token(lexeme.text, lexeme);
            expect("::");
            axis.children.add(expression(80));
            return axis;
        }

        private Token name(final Lexeme lexeme) {
            return new Token("(name)", lexeme.text, lexeme);
        }

        // A function call (or a kind test), its name the lexeme; prefix is the ':' token it follows, if any.
        private Token call(final Lexeme lexeme, final String namespace) {
            final String name = lexeme.text;
            if (namespace == null) {
                if ("function".equals(name)) {
                    throw new XPathParseException("inline functions are not read here");
                }
                if (XPathFunctions.SEQUENCE_TYPES.contains(name)) {
                    throw new XPathParseException(name + " is not allowed in an XPath expression (XPST0003)");
                }
            }
            final boolean registered = namespace != null && !XPathFunctions.isBuiltIn(namespace)
                                       && !MappingFacts.XS.equals(namespace);
            final Lexeme open = peek();
            // A function registered for a prefix the stylesheet binds, with a name the standard functions have too
            // (stroom:format-date, mcp:data), is placed at its bracket, as elementpath places it.
            final Token call = registered && XPathFunctions.isStandard(name)
                    ? new Token(name, name, open.start, open.end, TokenKind.FUNCTION)
                    : new Token(name, name, lexeme.start, lexeme.end, kindOf(name, namespace));
            expect("(");
            if (!isNext(")")) {
                call.children.add(expression(5));
                while (isNext(",")) {
                    advance();
                    call.children.add(expression(5));
                }
            }
            expect(")");
            if (namespace == null && !"attribute".equals(name) && !XPathFunctions.KIND_TESTS.contains(name)
                && !"map".equals(name) && !"array".equals(name)) {
                if (!XPathFunctions.isStandard(name)) {
                    throw new XPathParseException("unknown function '" + name + "' (XPST0017)");
                }
                if (!XPathFunctions.takes(name, call.children.size())) {
                    throw new XPathParseException("'fn:" + name + "' function: wrong number of arguments "
                                                  + "(XPST0017)");
                }
            }
            return call;
        }

        private TokenKind kindOf(final String name, final String namespace) {
            return namespace == null && "attribute".equals(name)
                    ? TokenKind.PLAIN
                    : TokenKind.FUNCTION;
        }

        private Token led(final Lexeme lexeme, final Token left) {
            final String symbol = lexeme.text;
            if (COMPARISONS.contains(symbol)) {
                if (COMPARISONS.contains(left.symbol) && left.children.size() == 2) {
                    throw unexpected(lexeme);
                }
                return binary(lexeme, left, expression(30));
            }
            return switch (symbol) {
                case "[" -> predicate(lexeme, left);
                case "(" -> dynamicCall(lexeme, left);
                case "?" -> binary(lexeme, left, lookupKey());
                case "#" -> binary(lexeme, left, number());
                case ":" -> prefixed(lexeme, left);
                case "instance" -> typed(lexeme, left, "of");
                case "treat", "castable", "cast" -> typed(lexeme, left, "as");
                case "=>" -> arrow(lexeme, left);
                case "/", "//" -> path(lexeme, left);
                default -> binary(lexeme, left, expression(BINDING.get(symbol)));
            };
        }

        // a/b, a//b: as elementpath, only after a step (a name, a call, a predicate, a variable, ...) and before
        // one (a lookup's result, say, is no step: $m?a/b is refused).
        private Token path(final Lexeme lexeme, final Token left) {
            final boolean isStep = Set.of("/", "//", ":", "[", "$").contains(left.symbol)
                                 || STEP_TOKENS.contains(left.symbol) && left.kind != TokenKind.MAP
                                 || left.kind == TokenKind.FUNCTION
                                 || "attribute".equals(left.symbol)
                                 || XPathFunctions.AXES.contains(left.symbol) && left.children.size() == 1;
            if (!isStep || !startsStep(peek())) {
                throw unexpected(lexeme);
            }
            step = true;
            return binary(lexeme, left, expression(75));
        }

        private Token binary(final Lexeme lexeme, final Token left, final Token right) {
            final Token operator = token(lexeme.text, lexeme);
            operator.children.add(left);
            operator.children.add(right);
            return operator;
        }

        private Token predicate(final Lexeme lexeme, final Token left) {
            final Token predicate = binary(lexeme, left, expression(0));
            expect("]");
            return predicate;
        }

        private Token dynamicCall(final Lexeme lexeme, final Token left) {
            final Token call = token("(", lexeme);
            call.children.add(left);
            if (!isNext(")")) {
                call.children.add(expression(5));
                while (isNext(",")) {
                    advance();
                    call.children.add(expression(5));
                }
            }
            expect(")");
            return call;
        }

        // What a lookup (?) reads: a name, a number, a bracketed expression or *.
        private Token lookupKey() {
            final Lexeme next = advance();
            if (next.kind == Kind.LITERAL && Character.isDigit(next.text.charAt(0)) && !next.text.contains(".")) {
                return literal(next);
            }
            if ("(".equals(next.text)) {
                return parenthesised(next);
            }
            if ("*".equals(next.text)) {
                return token("*", next);
            }
            // A name, not a function elementpath knows ($m?normalize-space(x) is refused, as it refuses it).
            if (WORD.matcher(next.text).matches() && !(isNext("(") && knownFunction(next.text))) {
                return name(next);
            }
            throw unexpected(next);
        }

        private boolean knownFunction(final String name) {
            return XPathFunctions.isStandard(name) || XPathFunctions.KIND_TESTS.contains(name)
                   || Set.of("attribute", "map", "array", "if", "function").contains(name);
        }

        private Token number() {
            final Lexeme next = advance();
            if (next.kind != Kind.LITERAL || !next.text.chars().allMatch(Character::isDigit)) {
                throw unexpected(next);
            }
            return literal(next);
        }

        private Token prefixed(final Lexeme colon, final Token left) {
            final Lexeme local = advance();
            final Lexeme after = peek();
            String namespace = null;
            if ("(name)".equals(left.symbol)) {
                final String prefix = (String) left.value;
                namespace = namespaces.containsKey(prefix)
                        ? namespaces.get(prefix)
                        : XPathFunctions.DEFAULT_NAMESPACES.get(prefix);
                if (namespace == null) {
                    throw new XPathParseException("prefix '" + prefix + "' is not declared (XPST0081)");
                }
            }
            final Token right;
            if (after != null && "(".equals(after.text) && WORD.matcher(local.text).matches()
                && namespace != null) {
                right = call(local, namespace);
                checkPrefixed((String) left.value, namespace, local.text, right.children.size());
            } else if ("*".equals(local.text)) {
                if ("*".equals(left.symbol)) {
                    throw unexpected(local);
                }
                right = token("*", local);
            } else if (WORD.matcher(local.text).matches()) {
                right = name(local);
            } else {
                throw unexpected(local);
            }
            final Token prefixed = new Token(":", left.value + ":" + right.value, colon);
            prefixed.children.add(left);
            prefixed.children.add(right);
            return prefixed;
        }

        // A prefixed call: a known function of the standard, map, array or math namespaces is called with the
        // arguments it takes; any other is registered (never run) for a prefix the stylesheet binds itself.
        private void checkPrefixed(final String prefix, final String namespace, final String name, final int count) {
            final boolean bound = namespaces.containsKey(prefix);
            if (MappingFacts.XS.equals(namespace)) {
                if (!XPathFunctions.CONSTRUCTORS.contains(name)) {
                    throw new XPathParseException("'xs:" + name + "' is not a name");
                }
                return;
            }
            if (XPathFunctions.isBuiltIn(namespace) && XPathFunctions.has(namespace, name)) {
                if (!XPathFunctions.takes(namespace, name, count)) {
                    throw new XPathParseException("'" + prefix + ":" + name + "' function: wrong number of "
                                                  + "arguments (XPST0017)");
                }
                return;
            }
            if (!bound) {
                throw new XPathParseException("prefix " + PyText.repr(prefix) + " isn't bound in the "
                                              + "stylesheet (XPST0081)");
            }
        }

        // instance of, treat as, castable as, cast as: the expression and its type (the occurrence indicator
        // is left out of the tree, as elementpath leaves it).
        private Token typed(final Lexeme lexeme, final Token left, final String word) {
            expect(word);
            final Token type;
            final Lexeme next = peek();
            final Lexeme after = peek(1);
            if (next != null && WORD.matcher(next.text).matches() && after != null && "(".equals(after.text)) {
                advance();
                type = kindTest(next);
            } else {
                type = expression(85);
            }
            final Lexeme occurrence = peek();
            final boolean cast = "cast".equals(lexeme.text) || "castable".equals(lexeme.text);
            if (occurrence != null && (cast
                    ? "?".equals(occurrence.text)
                    : Set.of("?", "*", "+").contains(occurrence.text))) {
                advance();
            }
            return binary(lexeme, left, type);
        }

        private Token kindTest(final Lexeme lexeme) {
            final Token test = new Token(lexeme.text, lexeme.text, lexeme.start, lexeme.end,
                    kindOf(lexeme.text, null));
            expect("(");
            if (!isNext(")")) {
                test.children.add(expression(5));
                while (isNext(",")) {
                    advance();
                    test.children.add(expression(5));
                }
            }
            expect(")");
            return test;
        }

        // a => f(b): the function's name (a call of nothing) and its arguments in brackets, as elementpath holds
        // them.
        private Token arrow(final Lexeme lexeme, final Token left) {
            final Lexeme name = advance();
            if (!WORD.matcher(name.text).matches()) {
                throw unexpected(name);
            }
            Token function = new Token(name.text, name.text, name.start, name.end, TokenKind.FUNCTION);
            if (isNext(":")) {
                final Lexeme colon = advance();
                final Lexeme local = advance();
                final Token prefix = name(name);
                final Token call = new Token(local.text, local.text, local.start, local.end, TokenKind.FUNCTION);
                function = new Token(":", name.text + ":" + local.text, colon);
                function.children.add(prefix);
                function.children.add(call);
            }
            final Lexeme open = expect("(");
            final Token arguments = token("(", open);
            if (!isNext(")")) {
                arguments.children.add(expression(5));
                while (isNext(",")) {
                    advance();
                    arguments.children.add(expression(5));
                }
            }
            expect(")");
            final Token arrow = token("=>", lexeme);
            arrow.children.add(left);
            arrow.children.add(function);
            arrow.children.add(arguments);
            return arrow;
        }

        private Token withChild(final Token token, final Token child) {
            token.children.add(child);
            return token;
        }

        private Token token(final String symbol, final Lexeme lexeme) {
            return new Token(symbol, symbol, lexeme);
        }

        private Lexeme advance() {
            if (at >= lexemes.size()) {
                throw new XPathParseException("unexpected end of source (XPST0003)");
            }
            return lexemes.get(at++);
        }

        private Lexeme peek() {
            return peek(0);
        }

        private Lexeme peek(final int ahead) {
            return at + ahead < lexemes.size()
                    ? lexemes.get(at + ahead)
                    : null;
        }

        private boolean isNext(final String text) {
            final Lexeme next = peek();
            return next != null && text.equals(next.text);
        }

        private Lexeme expect(final String text) {
            final Lexeme next = peek();
            if (next == null || !text.equals(next.text)) {
                throw unexpected(next);
            }
            return advance();
        }

        private XPathParseException unexpected() {
            return unexpected(peek());
        }

        private XPathParseException unexpected(final Lexeme lexeme) {
            if (lexeme == null) {
                return new XPathParseException("unexpected end of source (XPST0003)");
            }
            return new XPathParseException("unexpected " + PyText.repr(lexeme.text) + " at " + (lexeme.start + 1)
                                           + " in " + text + " (XPST0003)");
        }
    }


    // --------------------------------------------------------------------------------


    private enum Kind {
        LITERAL,
        SYMBOL,
        NAME,
        UNKNOWN
    }


    // --------------------------------------------------------------------------------


    private enum TokenKind {
        PLAIN,
        FUNCTION,
        MAP
    }


    // --------------------------------------------------------------------------------


    /// One lexeme: where it is and its text.
    private record Lexeme(int start, int end, String text, Kind kind) {

        // How it changes the depth of brackets: xpathtree counts a lexeme that is one of ([{ or )]}.
        private int depthChange() {
            if (text.length() == 1 && OPEN.contains(text)) {
                return 1;
            }
            if (text.length() == 1 && CLOSE.contains(text)) {
                return -1;
            }
            return 0;
        }
    }


    // --------------------------------------------------------------------------------


    /// A token of the parse, as elementpath holds it: its own place in the text (its lexeme) and its children.
    private static final class Token {

        private final String symbol;
        private final Object value;
        private final int start;
        private final int end;
        private final TokenKind kind;
        private final List<Token> children = new ArrayList<>();

        private Token(final String symbol, final Object value, final Lexeme lexeme) {
            this(symbol, value, lexeme.start, lexeme.end, TokenKind.PLAIN);
        }

        private Token(final String symbol,
                      final Object value,
                      final int start,
                      final int end,
                      final TokenKind kind) {
            this.symbol = symbol;
            this.value = value;
            this.start = start;
            this.end = end;
            this.kind = kind;
        }

        private List<Token> subtree() {
            final List<Token> out = new ArrayList<>();
            collect(this, out);
            return out;
        }

        private static void collect(final Token token, final List<Token> out) {
            out.add(token);
            for (final Token child : token.children) {
                collect(child, out);
            }
        }
    }
}
