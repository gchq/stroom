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
import { configure, addDecorator, addParameters } from "@storybook/react"; // <- or your storybook framework
import StroomDecorator from "../src/testing/storybook/StroomDecorator";

const req = require.context("../src", true, /\.stories\.tsx$/);
function loadStories() {
  req.keys().forEach((filename) => req(filename));
}

addDecorator(StroomDecorator);

addParameters({
  themes: [
    { name: "light", class: "theme-light", color: "#fff", default: true },
    { name: "dark", class: "theme-dark", color: "#000" },
  ],
});

configure(loadStories, module);
