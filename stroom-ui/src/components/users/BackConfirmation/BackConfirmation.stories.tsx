import * as React from "react";
import { storiesOf } from "@storybook/react";
import BackConfirmation from ".";
import { action } from "@storybook/addon-actions";

storiesOf("General Purpose/BackConfirmation", module)
  .add("form has valid data", () => (
    <BackConfirmation
      isOpen={true}
      onGoBack={action("onGoBack")}
      onContinueEditing={action("onContinueEditing")}
      onSaveAndGoBack={action("onSaveAndGoBack")}
      hasErrors={false}
    />
  ))
  .add("form does not have valid data", () => (
    <BackConfirmation
      isOpen={true}
      onGoBack={action("onGoBack")}
      onContinueEditing={action("onContinueEditing")}
      onSaveAndGoBack={action("onSaveAndGoBack")}
      hasErrors={true}
    />
  ));
