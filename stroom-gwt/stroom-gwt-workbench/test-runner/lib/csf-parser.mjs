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

// A small parser for the React project's CSF 3 story files (*.stories.tsx), used when no Storybook
// index.json is available and to cross-check one. It understands the conventions the React
// project uses throughout:
//
//   const meta = { title: 'Widgets/Buttons/Button', ... } satisfies Meta<...>;
//   export default meta;
//   export const WithIcons: Story = { name: 'With icons', play: async () => { ... } };
//
// The meta's title is its first `title:` property. Story properties are recognised at two spaces of indentation, i.e. directly in the story's
// object, so e.g. an arg called 'name' isn't mistaken for the story's name.

import { storyId } from './storybook-ids.mjs';

const STRING = String.raw`(['"\`])((?:\\.|(?!\1)[^\\])*)\1`;

function unquote(text) {
  return text.replace(/\\(.)/g, '$1');
}

// The end of the story's object: the end of its line if it is all on one line, e.g.
// `export const Empty: Story = {};`, otherwise the first line starting with '}'. Code between the
// stories, e.g. test data, isn't part of the story.
function storyEnd(source, start, nextExport) {
  const lineEnd = source.indexOf('\n', start);
  const firstLine = source.substring(start, lineEnd < 0 ? source.length : lineEnd);
  if (/[}\])];?\s*$/.test(firstLine) || lineEnd < 0) {
    return lineEnd < 0 ? source.length : lineEnd;
  }
  const closing = /^[})]/m.exec(source.substring(lineEnd + 1, nextExport));
  return closing ? lineEnd + 1 + closing.index + 1 : nextExport;
}

// Returns {title, stories: [{id, title, name, exportName, hasPlay}]} or throws if the file has
// no title.
export function parseCsf(source, file) {
  const defaultIndex = source.search(/^export default /m);
  const metaStart = source.search(/^const meta\b/m);
  const metaText = metaStart >= 0 && defaultIndex > metaStart
    ? source.substring(metaStart, defaultIndex)
    : source.substring(0, Math.max(defaultIndex, 0));
  const titleMatch = new RegExp(String.raw`(?:^|[{,\s])title:\s*${STRING}`).exec(metaText);
  if (!titleMatch) {
    throw new Error(`No title found in the meta of ${file}`);
  }
  const title = unquote(titleMatch[2]);
  const metaHasPlay = /^ {2}play\s*[:(]/m.test(metaText);

  const stories = [];
  const exportRegExp = /^export const ([A-Za-z_$][\w$]*)\b/gm;
  const matches = [...source.matchAll(exportRegExp)];
  matches.forEach((match, i) => {
    const nextExport = i + 1 < matches.length ? matches[i + 1].index : source.length;
    const body = source.substring(match.index, storyEnd(source, match.index, nextExport));
    const exportName = match[1];
    const nameMatch = new RegExp(String.raw`^ {2}name:\s*${STRING}`, 'm').exec(body);
    stories.push({
      id: storyId(title, exportName),
      title,
      name: nameMatch ? unquote(nameMatch[2]) : null,
      exportName,
      hasPlay: metaHasPlay || /^ {2}play\s*[:(]/m.test(body),
    });
  });
  return { title, stories };
}
