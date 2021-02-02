import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Dialog } from "components/Dialog/Dialog";
import { CreateTokenFormik } from "./CreateToken";

storiesOf("Token", module).add("Create Token", () => (
  <Dialog
    initWidth={348}
    initHeight={319}
    minWidth={348}
    minHeight={319}
    disableResize={true}
  >
    <CreateTokenFormik
      initialValues={{
        expiresOnMs: new Date().getTime(),
        userId: undefined,
      }}
      onSubmit={() => undefined}
      onClose={() => undefined}
    />
  </Dialog>
));
