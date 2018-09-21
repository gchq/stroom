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

// eslint-disable-next-line
import * as brace from "brace";
import "brace/mode/xml";
import "brace/theme/github";
import "brace/theme/ambiance";
import "brace/keybinding/vim";

import AceEditor, { AceEditorProps } from "react-ace";

import { GlobalStoreState } from "../../startup/reducers";

// The things I do to shut the compiler up...
if (brace) console.log("Brace found");

export interface Props extends AceEditorProps {}

const enhance = connect<AceEditorProps, {}, AceEditorProps>(
  ({ userSettings: { theme } }: GlobalStoreState) => ({
    theme: theme === "theme-light" ? "github" : "ambiance"
  }),
  {}
);

/**
 * This handles theme switching for the AceEditor. It also applies the vim keyboard handler,
 * because we'll want that everywhere we use the AceEditor.
 */
const ThemedAceEditor = (props: AceEditorProps) => (
  <AceEditor keyboardHandler="vim" {...props} />
);
export default enhance(ThemedAceEditor);
