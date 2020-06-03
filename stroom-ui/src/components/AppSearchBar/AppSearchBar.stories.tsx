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

import useForm from "lib/useForm";
import JsonDebug from "testing/JsonDebug";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import AppSearchBar from "./AppSearchBar";

interface Props {
  typeFilter?: string;
}

interface FormValues {
  chosenDocRef?: DocRefType;
}

const defaultValues: FormValues = {
  chosenDocRef: undefined,
};

let AppSearchAsForm: React.FunctionComponent<Props> = ({ typeFilter }) => {
  const { value, useControlledInputProps } = useForm<FormValues>({
    initialValues: defaultValues,
  });

  const chosenDocRefProps = useControlledInputProps<DocRefType>("chosenDocRef");

  return (
    <form>
      <p>{typeFilter ? `Only showing ${typeFilter}` : "Showing All Types"}</p>
      <div>
        <label>Chosen Doc Ref</label>
        <AppSearchBar typeFilter={typeFilter} {...chosenDocRefProps} />
      </div>

      <JsonDebug value={value}/>
    </form>
  );
};

const AppSearchAsNavigator: React.FunctionComponent<Props> = () => {
  const [chosenDocRef, setChosenDocRef] = React.useState<DocRefType>(undefined);
  return (
    <div style={{ height: "100%", width: "100%" }}>
      <AppSearchBar onChange={setChosenDocRef} value={chosenDocRef}/>
      <div tabIndex={0}>
        {chosenDocRef
          ? `Would be opening ${chosenDocRef.name}...`
          : "no doc ref chosen"}
      </div>
    </div>
  );
};

const stories = storiesOf(`App Search Bar`, module);
addThemedStories(stories, "Global Search", () => <AppSearchAsNavigator/>);
addThemedStories(stories, "In Form", () => <AppSearchAsForm/>);
addThemedStories(stories, "Specific Type", () => (
  <AppSearchAsForm typeFilter="Pipeline"/>
));
addThemedStories(stories, "Find Folder", () => <AppSearchAsForm typeFilter="Folder"/>);
