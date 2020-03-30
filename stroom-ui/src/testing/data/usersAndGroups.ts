import { loremIpsum } from "lorem-ipsum";
import { StroomUser } from "components/AuthorisationManager/api/userGroups";
import * as uuidv4 from "uuid/v4";

export const adminUser: StroomUser = {
  uuid: uuidv4(),
  name: "admin",
  group: false,
};

export const generateTestUser = (): StroomUser => ({
  uuid: uuidv4(),
  name: loremIpsum({ count: 3, units: "words" }),
  group: false,
});

export const generateTestGroup = (): StroomUser => ({
  uuid: uuidv4(),
  name: loremIpsum({ count: 3, units: "words" }),
  group: true,
});
