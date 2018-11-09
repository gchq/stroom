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
import { Formik, FormikProps } from "formik";
import { storiesOf } from "@storybook/react";

import PermissionInheritancePicker from "./PermissionInheritancePicker";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { addThemedStories } from "../../lib/themedStoryGenerator";
import "../../styles/main.css";

interface PermissionInheritanceForm {
  permissionInheritance?: string;
}

const TestForm = () => (
  <Formik
    initialValues={{ permissionInheritance: undefined, color: undefined }}
    onSubmit={() => console.log("Do nothing on submit")}
  >
    {({
      submitForm,
      values,
      setFieldValue
    }: FormikProps<PermissionInheritanceForm>) => (
      <form>
        <div>
          <label>Chosen Permission Inheritance</label>
          <PermissionInheritancePicker
            onChange={e => setFieldValue("permissionInheritance", e)}
            value={values.permissionInheritance}
          />
        </div>
        <div>
          <div>Permission Inheritance: {values.permissionInheritance}</div>
        </div>
      </form>
    )}
  </Formik>
);

const stories = storiesOf("Permission Inheritance Picker", module).addDecorator(
  StroomDecorator
);

addThemedStories(stories, <TestForm />);
