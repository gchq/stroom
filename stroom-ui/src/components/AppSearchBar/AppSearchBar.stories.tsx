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
import { connect } from "react-redux";
import { compose, withState } from "recompose";
import { Field, reduxForm, FormState } from "redux-form";

import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { addThemedStories } from "../../lib/themedStoryGenerator";
import AppSearchBar from "./AppSearchBar";

import "../../styles/main.css";
import { GlobalStoreState } from "../../startup/reducers";
import { DocRefType, DocRefConsumer } from "../../types";

interface Props {
  pickerId: string;
  typeFilters?: Array<string>;
}

interface ConnectState {
  thisForm: FormState;
}
interface ConnectDispatch {}

interface AsFormEnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhanceForm = compose<AsFormEnhancedProps, Props>(
  connect<ConnectState, {}, Props, GlobalStoreState>(
    ({ form }) => ({
      thisForm: form.appSearchBarTest
    }),
    {}
  ),
  reduxForm({
    form: "appSearchBarTest",
    enableReinitialize: true,
    touchOnChange: true
  })
);

let AppSearchAsForm = enhanceForm(
  ({ pickerId, typeFilters, thisForm }: AsFormEnhancedProps) => (
    <form>
      <div>
        <label htmlFor="someName">Some Name</label>
        <Field name="someName" component="input" type="text" />
      </div>
      <div>
        <label>Chosen Doc Ref</label>
        <Field
          name="chosenDocRef"
          component={({ input: { onChange, value } }) => (
            <AppSearchBar
              pickerId={pickerId}
              typeFilters={typeFilters}
              onChange={onChange}
              value={value}
            />
          )}
        />
      </div>
      {thisForm &&
        thisForm.values && (
          <div>
            <h3>Form Values Observed</h3>
            Name: {thisForm.values.someName}
            <br />
            Chosen Doc Ref:{" "}
            {thisForm.values.chosenDocRef && thisForm.values.chosenDocRef.name}
          </div>
        )}
    </form>
  )
);

interface WithPickedDocRef {
  pickedDocRef?: DocRefType | undefined;
  setPickedDocRef: DocRefConsumer;
}

interface AsPickerEnhancedProps extends Props, WithPickedDocRef {}

const enhancePicker = compose<AsPickerEnhancedProps, Props>(
  withState("pickedDocRef", "setPickedDocRef", undefined)
);

const AppSearchAsPicker = enhancePicker(
  ({
    pickerId,
    typeFilters,
    pickedDocRef,
    setPickedDocRef
  }: AsPickerEnhancedProps) => (
    <div>
      <AppSearchBar
        pickerId={pickerId}
        typeFilters={typeFilters}
        onChange={setPickedDocRef}
        value={pickedDocRef}
      />
      <div>Picked Doc Ref: {pickedDocRef && pickedDocRef.name}</div>
    </div>
  )
);

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
      <div>
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

const stories = storiesOf("App Search Bar", module);

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
