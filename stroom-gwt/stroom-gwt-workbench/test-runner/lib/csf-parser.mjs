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

// A small parser for the React project's CSF story files (*.stories.tsx), used when no Storybook
// index.json is available and to cross-check one. It isn't a full TypeScript parser: it scans the
// source for brackets, strings, template literals and comments, which is enough to find the
// top level properties of the meta and story objects however they are formatted, e.g.
//
//   const meta = { title: 'Widgets/Buttons/Button', ... } satisfies Meta<...>;
//   export default meta;                     // or: export default { title: '...', ... };
//   export const WithIcons: Story = { name: 'With icons', play: async () => { ... } };
//   export const Old = Template.bind({});    // CSF 2
//   Old.storyName = 'Old style';
//   const Other: Story = {...};
//   export { Other, Other as Renamed };      // export lists, including `meta as default`
//
// Property values may be followed by comments and TypeScript's `as ...`/`satisfies ...`, e.g.
// `title: 'Widgets/Glass' as const, // a note`.
//
// As in Storybook:
//   * a story's id is toId(meta.id ?? meta.title, storyNameFromExport(exportName)), unless the
//     story sets parameters.__id;
//   * every named export is a story, except __namedExportsOrder and those excluded by the meta's
//     includeStories/excludeStories (arrays of export names or regular expressions);
//   * __namedExportsOrder, if present, sets the order of the stories;
//   * a story has a play function if it or the meta has one.
//
// Files without a title (Storybook's auto-titles, which depend on the Storybook configuration)
// can't be parsed and throw an error. Export forms that can't be stories here (e.g. re-exports
// such as `export * from '...'` or `export { A } from '...'`, and `export class`) are reported
// as warnings.
//
// Known limitations: JSX text or a regular expression the scanner doesn't recognise as one (e.g.
// after a JSX '>') containing an unbalanced bracket or quote can confuse it (a quote only until the
// end of its line). The cross-check against an index (see react-manifest.mjs) reports any differences.

import { storyNameFromExport, toId } from './storybook-ids.mjs';

const STRING_LITERAL = /^(['"`])((?:\\.|(?!\1)[^\\])*)\1$/s;
const IDENTIFIER = String.raw`[A-Za-z_$][\w$]*`;
const OPENING = '{[(';
const CLOSING = '}])';

function unquote(text) {
  return text.replace(/\\(.)/g, '$1');
}

// The value of a string literal (without substitutions), or null if text isn't one.
function stringValue(text) {
  const match = STRING_LITERAL.exec(text.trim());
  if (!match || (match[1] === '`' && match[2].includes('${'))) {
    return null;
  }
  return unquote(match[2]);
}

// If a string, template literal or comment starts at i, returns the index after it, otherwise -1.
// A '...' or "..." string ends at the end of its line at the latest, so that e.g. a stray quote
// in JSX text only affects the rest of its line.
function skipNonCode(source, i) {
  const c = source[i];
  if (c === '/' && source[i + 1] === '/') {
    const end = source.indexOf('\n', i);
    return end < 0 ? source.length : end;
  }
  if (c === '/' && source[i + 1] === '*') {
    const end = source.indexOf('*/', i + 2);
    return end < 0 ? source.length : end + 2;
  }
  // A quote straight after a letter or digit can't start a string, so is e.g. an apostrophe in
  // JSX text such as <p>Don't</p>
  if ((c === '\'' || c === '"') && !(i > 0 && /[\w$]/.test(source[i - 1]))) {
    for (let j = i + 1; j < source.length; j++) {
      if (source[j] === '\\') {
        j++;
      } else if (source[j] === c) {
        return j + 1;
      } else if (source[j] === '\n') {
        return j;
      }
    }
    return source.length;
  }
  if (c === '/' && startsRegExp(source, i)) {
    return skipRegExp(source, i);
  }
  if (c === '`') {
    for (let j = i + 1; j < source.length; j++) {
      if (source[j] === '\\') {
        j++;
      } else if (source[j] === '`') {
        return j + 1;
      } else if (source[j] === '$' && source[j + 1] === '{') {
        j = findClose(source, j + 1);
      }
    }
    return source.length;
  }
  return -1;
}

// Whether a '/' at i starts a regular expression literal rather than being a division, judged by
// the code before it, as a tokeniser would. '<' and '>' aren't included, so that JSX closing tags
// such as '</div>' aren't mistaken for regular expressions.
function startsRegExp(source, i) {
  let j = i - 1;
  while (j >= 0 && /\s/.test(source[j])) {
    j--;
  }
  if (j < 0 || '(,=:[!&|?{};+-*%~^'.includes(source[j])) {
    return true;
  }
  // An arrow function's body, e.g. `(x) => /{/.test(x)`
  if (source[j] === '>' && source[j - 1] === '=') {
    return true;
  }
  return /\b(?:return|typeof|case|do|else|in|of|void|yield|await)$/.test(source.substring(Math.max(0, j - 6), j + 1));
}

// The index after the regular expression literal starting at i (including its flags), or i + 1
// if it doesn't end on the same line, i.e. isn't one after all.
function skipRegExp(source, i) {
  let inClass = false;
  for (let j = i + 1; j < source.length; j++) {
    const c = source[j];
    if (c === '\\') {
      j++;
    } else if (c === '\n') {
      return i + 1;
    } else if (c === '[') {
      inClass = true;
    } else if (c === ']') {
      inClass = false;
    } else if (c === '/' && !inClass) {
      let end = j + 1;
      while (end < source.length && /[a-z]/.test(source[end])) {
        end++;
      }
      return end;
    }
  }
  return i + 1;
}

// The index of the bracket closing the one at open, or the end of the source if there isn't one.
export function findClose(source, open) {
  let depth = 0;
  let i = open;
  while (i < source.length) {
    const skipped = skipNonCode(source, i);
    if (skipped >= 0) {
      i = skipped;
      continue;
    }
    const c = source[i];
    if (OPENING.includes(c)) {
      depth++;
    } else if (CLOSING.includes(c)) {
      depth--;
      if (depth === 0) {
        return i;
      }
    }
    i++;
  }
  return source.length;
}

// The text with its comments replaced by a space, leaving strings, template literals and
// regular expressions alone.
export function stripComments(text) {
  let out = '';
  let i = 0;
  while (i < text.length) {
    const skipped = skipNonCode(text, i);
    if (skipped >= 0) {
      const isComment = text[i] === '/' && (text[i + 1] === '/' || text[i + 1] === '*');
      out += isComment ? ' ' : text.substring(i, skipped);
      i = skipped;
    } else {
      out += text[i];
      i++;
    }
  }
  return out;
}

const TYPE_OPERATOR = /(?:as|satisfies)(?=\s)/y;

// The expression without a trailing TypeScript `as ...` or `satisfies ...` (outside any brackets,
// strings etc.), e.g. "'Widgets/Glass' as const" is "'Widgets/Glass'".
export function stripTypeSuffix(text) {
  let i = 0;
  while (i < text.length) {
    const skipped = skipNonCode(text, i);
    if (skipped >= 0) {
      i = skipped;
      continue;
    }
    if (OPENING.includes(text[i])) {
      i = findClose(text, i) + 1;
      continue;
    }
    if (i > 0 && /\s/.test(text[i - 1])) {
      TYPE_OPERATOR.lastIndex = i;
      if (TYPE_OPERATOR.test(text)) {
        return text.substring(0, i).trim();
      }
    }
    i++;
  }
  return text.trim();
}

// The text up to its first ';' outside any brackets, strings etc., e.g. the value of an assignment
// followed by another statement on the same line.
function untilSemicolon(text) {
  let i = 0;
  while (i < text.length && text[i] !== ';') {
    const skipped = skipNonCode(text, i);
    if (skipped >= 0) {
      i = skipped;
    } else if (OPENING.includes(text[i])) {
      i = findClose(text, i) + 1;
    } else {
      i++;
    }
  }
  return text.substring(0, i);
}

// A property or assignment's value as written, without comments or a type suffix.
function cleanValue(text) {
  return stripTypeSuffix(stripComments(text).trim());
}

// Skips white space and comments.
function skipSpace(source, i, end) {
  while (i < end) {
    if (/\s/.test(source[i])) {
      i++;
    } else if (source[i] === '/' && (source[i + 1] === '/' || source[i + 1] === '*')) {
      i = skipNonCode(source, i);
    } else {
      break;
    }
  }
  return i;
}

// The index of the next ',' at this level, or end.
function nextComma(source, i, end) {
  while (i < end) {
    const skipped = skipNonCode(source, i);
    if (skipped >= 0) {
      i = skipped;
    } else if (OPENING.includes(source[i])) {
      i = findClose(source, i) + 1;
    } else if (source[i] === ',') {
      return i;
    } else {
      i++;
    }
  }
  return end;
}

const METHOD_KEY = new RegExp(String.raw`(?:async\s+|get\s+|set\s+)?\*?\s*(${IDENTIFIER})\s*\(`, 'y');
const PLAIN_KEY = new RegExp(String.raw`(${IDENTIFIER})|'((?:\\.|[^'\\])*)'|"((?:\\.|[^"\\])*)"`, 'y');

// Returns the top level properties of the object literal whose '{' is at open, as a map of name to
// {kind: 'value' | 'method' | 'shorthand', value: <the value's source, for 'value'>}, and the
// index of its closing '}'.
export function objectProperties(source, open) {
  const close = findClose(source, open);
  const properties = new Map();
  let i = open + 1;
  while (i < close) {
    i = skipSpace(source, i, close);
    if (i >= close) {
      break;
    }
    const end = nextComma(source, i, close);
    METHOD_KEY.lastIndex = i;
    const method = METHOD_KEY.exec(source);
    if (method && method.index === i && METHOD_KEY.lastIndex <= end) {
      properties.set(method[1], { kind: 'method', value: null });
    } else {
      PLAIN_KEY.lastIndex = i;
      const key = PLAIN_KEY.exec(source);
      if (key && key.index === i) {
        const name = key[1] ?? unquote(key[2] ?? key[3]);
        const after = skipSpace(source, PLAIN_KEY.lastIndex, end);
        if (source[after] === ':') {
          properties.set(name, { kind: 'value', value: cleanValue(source.substring(after + 1, end)) });
        } else if (after >= end && key[1]) {
          properties.set(name, { kind: 'shorthand', value: null });
        }
      }
    }
    i = end + 1;
  }
  return { close, properties };
}

// Calls visit(index) for each position where a top level statement may start, i.e. the start of
// each line, or the code after a ';', that isn't inside brackets, a string or a comment (after
// any spaces or tabs).
function forEachStatementStart(source, visit) {
  let atStart = true;
  let i = 0;
  while (i < source.length) {
    if (atStart) {
      while (source[i] === ' ' || source[i] === '\t') {
        i++;
      }
      if (i >= source.length) {
        break;
      }
      visit(i);
    }
    const skipped = skipNonCode(source, i);
    if (skipped >= 0) {
      atStart = false;
      i = skipped;
    } else if (OPENING.includes(source[i])) {
      atStart = false;
      i = findClose(source, i) + 1;
    } else {
      atStart = source[i] === '\n' || source[i] === ';';
      i++;
    }
  }
}

// The 1-based line number of index in source.
function lineOf(source, index) {
  return source.substring(0, index).split('\n').length;
}

// The values of the string literals in text, e.g. an array of them.
function stringLiterals(text) {
  return [...text.matchAll(/(['"`])((?:\\.|(?!\1)[^\\])*)\1/g)].map((match) => unquote(match[2]));
}

// Parses an array of string literals or a regular expression literal, as used by
// includeStories/excludeStories, returning a predicate on export names, or null.
function exportMatcher(text) {
  if (!text) {
    return null;
  }
  const trimmed = text.trim();
  const regExp = /^\/((?:\\.|[^\\/])+)\/([a-z]*)$/.exec(trimmed);
  if (regExp) {
    const pattern = new RegExp(regExp[1], regExp[2]);
    return (name) => pattern.test(name);
  }
  if (trimmed.startsWith('[')) {
    const names = stringLiterals(trimmed);
    return (name) => names.includes(name);
  }
  return null;
}

function propertyValue(properties, name) {
  const property = properties.get(name);
  return property?.kind === 'value' ? property.value : null;
}

function hasProperty(properties, name) {
  const property = properties.get(name);
  // e.g. `play: undefined` isn't a play function
  return property !== undefined && !(property.kind === 'value' && /^(undefined|null)$/.test(property.value));
}

// Words that can follow `export default` without it being the meta's name.
const NOT_META_NAMES = new Set(['function', 'class', 'async', 'new', 'await']);
const EXPORT_LIST_ITEM = new RegExp(
  String.raw`^(${IDENTIFIER})(?:\s+as\s+(${IDENTIFIER}|'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"))?$`);

// Returns {title, stories: [{id, title, name, exportName, hasPlay}], warnings}, where name is null
// if the story doesn't set one, and warnings describes each export that was ignored as it can't be
// parsed (e.g. a re-export from another file). Throws if the file has no meta or the meta has no
// title.
export function parseCsf(source, file) {
  const statements = [];
  forEachStatementStart(source, (index) => statements.push(index));
  const at = (regExp, index) => {
    regExp.lastIndex = index;
    const match = regExp.exec(source);
    return match && match.index === index ? match : null;
  };
  const warnings = [];
  const warn = (index, message) => warnings.push(`${file}:${lineOf(source, index)}: ${message}`);
  const statementText = (index) => {
    const lineEnd = source.indexOf('\n', index);
    const text = source.substring(index, lineEnd < 0 ? source.length : lineEnd).trim();
    return text.length > 60 ? `${text.substring(0, 60)}...` : text;
  };

  // Declarations (exported or not) and their values, e.g. `const meta = {...}` or
  // `export const Primary: Story = {...}`, and `export function Old() {...}` (CSF 2)
  const declarationRegExp = new RegExp(
    String.raw`(export\s+)?(?:(?:const|let|var)\s+(${IDENTIFIER})\b[^=;]*=\s*|(?:async\s+)?function\s*\*?\s*(${IDENTIFIER}))`,
    'y');
  // `export { A, B as C }`, `export type { T }` and `export { A } from '...'`
  const exportListRegExp = /export\s+(type\s+)?\{/y;
  // The meta: `export default {...}` or `export default meta`
  const defaultRegExp = new RegExp(String.raw`export\s+default\s+(?:(\{)|(${IDENTIFIER}))`, 'y');
  // Exports that only declare types
  const typeExportRegExp = /export\s+(?:type|interface|declare)\b/y;
  const anyExportRegExp = /export\b/y;
  // CSF 2 assignments such as `Story.storyName = '...'`
  const assignmentRegExp = new RegExp(String.raw`(${IDENTIFIER})\.(storyName|play)\s*=\s*`, 'y');

  // The index of each declared name's value (-1 for a function), the named exports in order, and
  // the assignments
  const locals = new Map();
  const exports = [];
  const assignments = new Map();
  let sawDefault = false;
  let metaOpen = -1;
  let metaName = null;
  for (const index of statements) {
    const declaration = at(declarationRegExp, index);
    if (declaration) {
      const name = declaration[2] ?? declaration[3];
      if (!locals.has(name)) {
        locals.set(name, declaration[2] ? declarationRegExp.lastIndex : -1);
      }
      if (declaration[1]) {
        exports.push({ exportName: name, localName: name, index });
      }
      continue;
    }
    const list = at(exportListRegExp, index);
    if (list) {
      const open = exportListRegExp.lastIndex - 1;
      const close = findClose(source, open);
      if (list[1]) {
        // Only types
        continue;
      }
      if (/^\s*from\b/.test(stripComments(source.substring(close + 1, close + 200)))) {
        warn(index, `ignored '${statementText(index)}': re-exports from other files aren't supported`);
        continue;
      }
      for (const item of stripComments(source.substring(open + 1, close)).split(',')) {
        const text = item.trim();
        if (text === '' || /^type\s/.test(text)) {
          continue;
        }
        const match = EXPORT_LIST_ITEM.exec(text);
        if (!match) {
          warn(index, `ignored '${text}' in an export list, as it couldn't be parsed`);
          continue;
        }
        const exportName = match[2] ? (stringValue(match[2]) ?? match[2]) : match[1];
        if (exportName !== 'default') {
          exports.push({ exportName, localName: match[1], index });
        } else if (!sawDefault) {
          sawDefault = true;
          metaName = match[1];
        }
      }
      continue;
    }
    const defaultExport = at(defaultRegExp, index);
    if (defaultExport) {
      if (!sawDefault) {
        sawDefault = true;
        metaOpen = defaultExport[1] ? defaultRegExp.lastIndex - 1 : -1;
        metaName = defaultExport[2] && !NOT_META_NAMES.has(defaultExport[2]) ? defaultExport[2] : null;
      }
      continue;
    }
    if (at(anyExportRegExp, index)) {
      if (!at(typeExportRegExp, index)) {
        warn(index, `ignored '${statementText(index)}': this form of export isn't supported`);
      }
      continue;
    }
    const assignment = at(assignmentRegExp, index);
    if (assignment) {
      const valueStart = assignmentRegExp.lastIndex;
      const lineEnd = source.indexOf('\n', valueStart);
      const line = source.substring(valueStart, lineEnd < 0 ? source.length : lineEnd);
      assignments.set(`${assignment[1]}.${assignment[2]}`, cleanValue(untilSemicolon(line)));
    }
  }

  if (metaName && (locals.get(metaName) ?? -1) >= 0 && source[locals.get(metaName)] === '{') {
    metaOpen = locals.get(metaName);
  }
  if (metaOpen < 0) {
    throw new Error(`No meta (default export object) found in ${file}`);
  }
  const { properties: meta } = objectProperties(source, metaOpen);
  const title = stringValue(propertyValue(meta, 'title') ?? '');
  if (title === null) {
    throw new Error(`No title found in the meta of ${file} (Storybook's auto-titles aren't supported)`);
  }
  const componentId = stringValue(propertyValue(meta, 'id') ?? '') ?? title;
  const metaHasPlay = hasProperty(meta, 'play');
  const include = exportMatcher(propertyValue(meta, 'includeStories'));
  const exclude = exportMatcher(propertyValue(meta, 'excludeStories'));

  let namedExportsOrder = null;
  const storyExports = [];
  for (const entry of exports) {
    const valueStart = locals.get(entry.localName) ?? -1;
    if (entry.exportName === '__namedExportsOrder') {
      namedExportsOrder = source[valueStart] === '['
        ? stringLiterals(source.substring(valueStart, findClose(source, valueStart) + 1))
        : null;
      continue;
    }
    if (!locals.has(entry.localName)) {
      warn(entry.index, `'${entry.localName}' is exported but not declared in the file (e.g. it is `
        + 'imported), so its name and play function are unknown');
    }
    storyExports.push({ ...entry, valueStart });
  }

  let stories = storyExports
    .filter(({ exportName }) => (!include || include(exportName)) && (!exclude || !exclude(exportName)))
    .map(({ exportName, localName, valueStart }) => {
      const properties = valueStart >= 0 && source[valueStart] === '{'
        ? objectProperties(source, valueStart).properties
        : new Map();
      const name = stringValue(propertyValue(properties, 'name') ?? '')
        ?? stringValue(propertyValue(properties, 'storyName') ?? '')
        ?? stringValue(assignments.get(`${localName}.storyName`) ?? '');
      const parametersText = propertyValue(properties, 'parameters');
      const parameters = parametersText?.startsWith('{')
        ? objectProperties(parametersText, 0).properties
        : new Map();
      const explicitId = stringValue(propertyValue(parameters, '__id') ?? '');
      return {
        id: explicitId ?? toId(componentId, storyNameFromExport(exportName)),
        title,
        name,
        exportName,
        hasPlay: metaHasPlay || hasProperty(properties, 'play') || assignments.has(`${localName}.play`),
      };
    });
  if (namedExportsOrder) {
    const position = (story) => {
      const index = namedExportsOrder.indexOf(story.exportName);
      return index < 0 ? Number.MAX_SAFE_INTEGER : index;
    };
    stories = stories
      .map((story, i) => ({ story, i }))
      .sort((a, b) => position(a.story) - position(b.story) || a.i - b.i)
      .map(({ story }) => story);
  }
  return { title, stories, warnings };
}
