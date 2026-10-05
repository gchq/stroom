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

// Storybook's rules for story ids and names, copied from storybook/internal/csf (Storybook 10), so
// that the ids computed from the React *.stories.tsx files are exactly those Storybook uses. The
// GWT workbench has the same rules in Java (stroom.gwt.workbench.framework.client.story.StoryIds).

// Storybook's sanitize(): lower case, with runs of punctuation/space replaced by a single '-'
export function sanitize(string) {
  return string
    .toLowerCase()
    .replace(/[ ’–—―′¿'`~!@#$%^&*()_|+\-=?;:'",.<>{}[\]\\/]/gi, '-')
    .replace(/-+/g, '-')
    .replace(/^-+/, '')
    .replace(/-+$/, '');
}

function sanitizeSafe(string, part) {
  const sanitized = sanitize(string);
  if (sanitized === '') {
    throw new Error(`Invalid ${part} '${string}', must include alphanumeric characters`);
  }
  return sanitized;
}

// Storybook's toId(), e.g. toId('Widgets/Buttons/Button', 'With Icons') =
// 'widgets-buttons-button--with-icons'
export function toId(kind, name) {
  return `${sanitizeSafe(kind, 'kind')}${name ? `--${sanitizeSafe(name, 'name')}` : ''}`;
}

// Storybook's storyNameFromExport(), e.g. 'WithIcons' => 'With Icons', 'S3Config' => 'S 3 Config'
export function storyNameFromExport(key) {
  return key
    .replace(/_/g, ' ')
    .replace(/-/g, ' ')
    .replace(/\./g, ' ')
    .replace(/([^\n])([A-Z])([a-z])/g, (str, $1, $2, $3) => `${$1} ${$2}${$3}`)
    .replace(/([a-z])([A-Z])/g, (str, $1, $2) => `${$1} ${$2}`)
    .replace(/([a-z])([0-9])/gi, (str, $1, $2) => `${$1} ${$2}`)
    .replace(/([0-9])([a-z])/gi, (str, $1, $2) => `${$1} ${$2}`)
    .replace(/(\s|^)(\w)/g, (str, $1, $2) => `${$1}${$2.toUpperCase()}`)
    .replace(/ +/g, ' ')
    .trim();
}

// The id of a story exported as exportName from a CSF file with the given title. As in
// Storybook, a story's `name:` only changes its display name, not its id.
export function storyId(title, exportName) {
  return toId(title, storyNameFromExport(exportName));
}
