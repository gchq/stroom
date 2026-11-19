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

// Selecting stories by pattern and tags.

function globToRegExp(glob) {
  let source = '';
  for (const c of glob) {
    if (c === '*') {
      source += '.*';
    } else if (c === '?') {
      source += '.';
    } else {
      source += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
    }
  }
  return new RegExp(`^${source}$`, 'i');
}

// Makes a predicate for a pattern given on the command line:
//   * containing '/' - matches the story's title, e.g. 'Widgets/Buttons' (a prefix) or
//     'Widgets/*/TickBox' (a glob);
//   * otherwise matches the story's id, e.g. 'widgets-buttons-' (a prefix) or
//     '*--with-icons' (a glob).
// Matching ignores case.
export function patternMatcher(pattern) {
  const byTitle = pattern.includes('/');
  const isGlob = /[*?]/.test(pattern);
  const regExp = isGlob ? globToRegExp(pattern) : null;
  const lowerPattern = pattern.toLowerCase();
  return (story) => {
    const value = byTitle ? story.title : story.id;
    if (regExp) {
      return regExp.test(value);
    }
    const lowerValue = value.toLowerCase();
    // A title prefix must end at a path separator, so 'Widgets/Button' doesn't match 'Widgets/Buttons'
    return byTitle
      ? lowerValue === lowerPattern || lowerValue.startsWith(lowerPattern.replace(/\/$/, '') + '/')
      : lowerValue.startsWith(lowerPattern);
  };
}

// Selects the stories to run, in index order. Each story gets `skip: true` if it has one of the
// skip tags. Stories are included if they match any of the include patterns (or there are none),
// don't match any exclude pattern, have one of the include tags (if any) and none of the exclude
// tags.
export function selectStories(stories, options = {}) {
  const includes = (options.include ?? []).map(patternMatcher);
  const excludes = (options.exclude ?? []).map(patternMatcher);
  const includeTags = options.includeTags ?? [];
  const excludeTags = options.excludeTags ?? [];
  const skipTags = options.skipTags ?? [];
  const hasAny = (story, tags) => tags.some((tag) => (story.tags ?? []).includes(tag));
  return stories
    .filter((story) => includes.length === 0 || includes.some((matches) => matches(story)))
    .filter((story) => !excludes.some((matches) => matches(story)))
    .filter((story) => includeTags.length === 0 || hasAny(story, includeTags))
    .filter((story) => !hasAny(story, excludeTags))
    .map((story) => ({ ...story, skip: hasAny(story, skipTags) }));
}
