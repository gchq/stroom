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
import { compose } from "recompose";
import { connect } from "react-redux";
import { Field, reduxForm, FormState } from "redux-form";
import { storiesOf } from "@storybook/react";

import PermissionInheritancePicker from "./PermissionInheritancePicker";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { GlobalStoreState } from "../../startup/reducers";
import { addThemedStories } from "../../lib/themedStoryGenerator";
import "../../styles/main.css";

interface Props {}
interface ConnectState {
  thisForm: FormState;
}
interface ConnectDispatch {}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ form }) => ({ thisForm: form.permissionInheritanceTest }),
    {}
  ),
  reduxForm({
    form: "permissionInheritanceTest"
  })
);

const TestForm = enhance(({ thisForm }: EnhancedProps) => (
  <form>
    <div>
      <label>Chosen Permission Inheritance</label>
      <Field
        name="permissionInheritance"
        component={({ input: { onChange, value } }) => (
          <PermissionInheritancePicker onChange={onChange} value={value} />
        )}
      />
    </div>
    {thisForm &&
      thisForm.values && (
        <div>
          <div>
            Permission Inheritance: {thisForm.values.permissionInheritance}
          </div>
        </div>
      )}
  </form>
));

const stories = storiesOf("Permission Inheritance Picker", module).addDecorator(
  StroomDecorator
);

addThemedStories(stories, <TestForm />);
