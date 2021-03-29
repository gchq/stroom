import { storiesOf } from "@storybook/react";
import * as React from "react";
import { EditTokenFormik } from "./EditToken";
import { ResizableDialog } from "components/Dialog/ResizableDialog";

storiesOf("Token", module).add("Edit Token", () => (
  <ResizableDialog
    initWidth={816}
    initHeight={622}
    minWidth={816}
    minHeight={622}
    disableResize={true}
  >
    <EditTokenFormik
      initialValues={{}}
      onSubmit={() => undefined}
      onClose={() => undefined}
    />
  </ResizableDialog>
));
