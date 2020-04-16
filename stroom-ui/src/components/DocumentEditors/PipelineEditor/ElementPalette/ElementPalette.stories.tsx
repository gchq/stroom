/*
 * Copyright 2018 Crown Copyright
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
import * as React from "react";
import { storiesOf } from "@storybook/react";

import ElementPalette from "./ElementPalette";

import { fullTestData } from "testing/data";
import { PipelineDocumentType } from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";

const showDeleteElementDialog = () =>
  console.error("Deleting Elements Not Supported in this Story");

const stories = storiesOf("Document Editors/Pipeline/Element Palette", module);

addThemedStories(stories, () => {
  return (
    <ElementPalette
      pipeline={
        Object.values(
          fullTestData.documents.Pipeline,
        )[0] as PipelineDocumentType
      }
      showDeleteElementDialog={showDeleteElementDialog}
    />
  );
});
