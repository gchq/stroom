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

import IndexFieldEditor, { useEditor } from "./IndexFieldEditor";
import { generateTestField } from "testing/data/indexDocs";
import Button from "../../../Button";
import JsonDebug from "testing/JsonDebug";
import { IndexField } from "components/DocumentEditors/useDocumentApi/types/indexDoc";

const FIELD_ID = 1007;

const TestHarness: React.FunctionComponent = () => {
  const testField = React.useMemo(generateTestField, []);
  const [indexField, setIndexField] = React.useState<IndexField>(testField);
  const [lastId, setLastId] = React.useState<number>(0);
  const { componentProps, showEditor } = useEditor((_id, _indexField) => {
    setLastId(_id);
    setIndexField(_indexField);
  });

  const onClick = React.useCallback(() => {
    showEditor(FIELD_ID, indexField);
  }, [showEditor, indexField]);

  return (
    <div>
      <h2>Index Field Editor</h2>
      <Button text="Edit" onClick={onClick} />
      <IndexFieldEditor {...componentProps} />
      <JsonDebug value={{ FIELD_ID, lastId, indexField }} />
    </div>
  );
};

storiesOf("Document Editors/Index", module).add("Field Editor", () => (
  <TestHarness />
));
