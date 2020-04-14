import * as uuidv4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { XMLSchemaDoc } from "components/DocumentEditors/useDocumentApi/types/xmlSchema";

export const generate = (): XMLSchemaDoc => ({
  type: "XMLSchema",
  uuid: uuidv4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
