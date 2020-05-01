import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import EditTokenForm from "./EditTokenForm";
import { action } from "@storybook/addon-actions";
import { Token } from "../api/types";
import * as moment from "moment";

const stories = storiesOf("Tokens/Edit", module);

const token: Token = {
  id: "1",
  version: 1,
  createTimeMs: moment().milliseconds(),
  updateTimeMs: moment().milliseconds(),
  createUser: "test user",
  updateUser: "test user",
  userEmail: "userEmail",
  tokenType: "api",
  data: "token string",
  expiresOnMs: moment().milliseconds(),
  comments: "Test",
  enabled: true,
};

addThemedStories(stories, () => (
  <EditTokenForm
    onChangeState={action("onChangeState")}
    onBack={action("onBack")}
    token={token}
  />
));
