import * as uuidv4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { ScriptDoc } from "components/DocumentEditors/useDocumentApi/types/scriptDoc";

export const generate = (): ScriptDoc => ({
  type: "Script",
  uuid: uuidv4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
