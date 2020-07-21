import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { SystemDoc } from "components/DocumentEditors/useDocumentApi/types/system";

export const generate = (): SystemDoc => ({
  type: "System",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
