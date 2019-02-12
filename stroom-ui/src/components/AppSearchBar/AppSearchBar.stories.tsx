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
import { useState } from "react";
import { Formik, Field, FieldProps } from "formik";

import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { addThemedStories } from "../../lib/themedStoryGenerator";
import AppSearchBar from "./AppSearchBar";

import "../../styles/main.css";
import { DocRefType } from "../../types";
import FormikDebug from "../../lib/FormikDebug";

interface Props {
  pickerId: string;
  typeFilters?: Array<string>;
}

let AppSearchAsForm = ({ pickerId, typeFilters }: Props) => (
  <Formik
    initialValues={{
      someName: "",
      chosenDocRef: undefined
    }}
    onSubmit={() => {}}
  >
    {({ setFieldValue }: Formik) => (
      <React.Fragment>
        <form>
          <div>
            <label htmlFor="someName">Some Name</label>
            <Field name="someName" type="text" />
          </div>
          <div>
            <label>Chosen Doc Ref</label>
            <Field name="chosenDocRef">
              {({ field: { value } }: FieldProps) => (
                <AppSearchBar
                  pickerId={pickerId}
                  typeFilters={typeFilters}
                  onChange={e => setFieldValue("chosenDocRef", e)}
                  value={value}
                />
              )}
            </Field>
          </div>
        </form>
        <FormikDebug />
      </React.Fragment>
    )}
  </Formik>
);

const AppSearchAsPicker = ({ pickerId, typeFilters }: Props) => {
  const [pickedDocRef, setPickedDocRef] = useState<DocRefType | undefined>(
    undefined
  );

  return (
    <div>
      <AppSearchBar
        pickerId={pickerId}
        typeFilters={typeFilters}
        onChange={setPickedDocRef}
        value={pickedDocRef}
      />
      <div>Picked Doc Ref: {pickedDocRef && pickedDocRef.name}</div>
    </div>
  );
};

class AppSearchAsNavigator extends React.Component<
  Props,
  { chosenDocRef?: DocRefType }
> {
  displayRef: React.RefObject<HTMLDivElement>;
  constructor(props: Props) {
    super(props);

    this.displayRef = React.createRef();
    this.state = {
      chosenDocRef: undefined
    };
  }
  render() {
    const { pickerId } = this.props;

    return (
      <div style={{ height: "100%", width: "100%" }}>
        <AppSearchBar
          pickerId={pickerId}
          onChange={d => {
            console.log("App Search Bar Chose a Value", d);
            this.setState({ chosenDocRef: d });
            this.displayRef.current!.focus();
          }}
          value={this.state.chosenDocRef}
        />
        <div tabIndex={0} ref={this.displayRef}>
          {this.state.chosenDocRef
            ? `Would be opening ${this.state.chosenDocRef.name}...`
            : "no doc ref chosen"}
        </div>
      </div>
    );
  }
}

const stories = storiesOf("Doc Ref/App Search Bar", module);

stories
  .addDecorator(StroomDecorator)
  .add("Search Bar (global)", () => (
    <AppSearchAsNavigator pickerId="global-search" />
  ))
  .add("Doc Ref Form", () => <AppSearchAsForm pickerId="docRefForm1" />)
  .add("Doc Ref Picker", () => <AppSearchAsPicker pickerId="docRefPicker2" />)
  .add("Doc Ref Form (Pipeline)", () => (
    <AppSearchAsForm pickerId="docRefForm3" typeFilters={["Pipeline"]} />
  ))
  .add("Doc Ref Picker (Feed AND Dictionary)", () => (
    <AppSearchAsPicker
      pickerId="docRefPicker4"
      typeFilters={["Feed", "Dictionary"]}
    />
  ))
  .add("Doc Ref Form (Folders)", () => (
    <AppSearchAsForm pickerId="docRefForm5" typeFilters={["Folder"]} />
  ));

addThemedStories(stories, <AppSearchAsNavigator pickerId="global-search" />);
