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

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { DocRefInfoModal } from ".";

import "../../styles/main.css";
import { useDocRefInfoDialog } from "./DocRefInfoModal";
import Button from "../Button";
import fullTestData from "../../lib/storybook/fullTestData";

const testFolder1 = fullTestData.documentTree.children![0];

storiesOf("Doc Ref/Info Modal", module)
  .addDecorator(StroomDecorator)
  .add("Doc Ref Info Modal", () => {
    const { showDialog, componentProps } = useDocRefInfoDialog();

    return (
      <div>
        <Button text="show" onClick={() => showDialog(testFolder1)} />
        <DocRefInfoModal {...componentProps} />
      </div>
    );
  });
