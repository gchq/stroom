import { IndexDoc } from "src/types";
import * as uuidv4 from "uuid/v4";
import * as loremIpsum from "lorem-ipsum";

export const generateTestIndex = (): IndexDoc => ({
  type: "Index",
  uuid: uuidv4(),
  name: loremIpsum({ count: 2, units: "words" })
});
