import * as uuidv4 from "uuid/v4";
import * as loremIpsum from "lorem-ipsum";
import { Dictionary } from "src/types";

export const generateTestDictionary = (): Dictionary => ({
  type: "Dictionary",
  uuid: uuidv4(),
  name: loremIpsum({ count: 2, units: "words" }),
  description: loremIpsum({ count: 6, units: "words" }),
  data: Array(10)
    .fill(null)
    .map(() => loremIpsum({ count: 1, units: "words" }))
    .join("\n"),
  imports: []
});
