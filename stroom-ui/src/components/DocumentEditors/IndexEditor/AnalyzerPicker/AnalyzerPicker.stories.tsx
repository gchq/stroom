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

import { addThemedStories } from "testing/storybook/themedStoryGenerator";

import AnalyzerPicker from "./AnalyzerPicker";
import { AnalyzerType } from "components/DocumentEditors/useDocumentApi/types/indexDoc";

const stories = storiesOf("Document Editors/Index/Analyzer Picker", module);

const B: React.FunctionComponent = () => {
  const [value, onChange] = React.useState<AnalyzerType | undefined>(undefined);

  return (
    <form>
      <label>Analyzer</label>
      <AnalyzerPicker value={value} onChange={onChange} />
    </form>
  );
};

addThemedStories(stories, () => <B />);
