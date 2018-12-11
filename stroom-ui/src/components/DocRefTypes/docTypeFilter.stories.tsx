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

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { storiesOf } from "@storybook/react";
import { Formik, Field, FieldProps } from "formik";

import DocTypeFilters from "./DocTypeFilters";
import DocRefTypePicker from "./DocRefTypePicker";

import "../../styles/main.css";
import FormikDebug from "../../lib/FormikDebug";

const TestForm = () => (
  <Formik
    initialValues={{
      docType: undefined,
      docTypes: []
    }}
    onSubmit={() => {}}
  >
    {({ setFieldValue }: Formik) => (
      <form>
        <div>
          <label>Chosen Doc Type</label>
          <Field name="docType">
            {({ field: { value } }: FieldProps) => (
              <DocRefTypePicker
                pickerId="test1"
                onChange={d => setFieldValue("docType", d)}
                value={value}
              />
            )}
          </Field>
        </div>
        <div>
          <label>Chosen Doc Types</label>
          <Field name="docTypes">
            {({ field: { value } }: FieldProps) => (
              <DocTypeFilters
                onChange={d => setFieldValue("docTypes", d)}
                value={value}
              />
            )}
          </Field>
        </div>
        <FormikDebug />
      </form>
    )}
  </Formik>
);

storiesOf("Pickers/Doc Ref Type", module)
  .addDecorator(StroomDecorator)
  .add("Doc Type Filter", () => <TestForm />);
