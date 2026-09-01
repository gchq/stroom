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

package stroom.ai.api;

import java.nio.file.Path;

/**
 * One table of data to be summarised, held as a markdown file so that data of any size is read a row at a
 * time rather than held in memory.
 *
 * @param description  How this source is named in logs, e.g. {@code "attachment 42"}. A source that cannot
 *                     be read is skipped rather than failing the whole summary, so this is what tells you
 *                     which one was skipped.
 * @param markdownFile The markdown table. The first two lines must be the header and separator rows, which
 *                     are repeated at the top of every batch the table is split into.
 * @param truncated    Whether this is only the leading rows of a larger result set, which the reader of the
 *                     summary is told about.
 */
public record TableSource(String description, Path markdownFile, boolean truncated) {

}
