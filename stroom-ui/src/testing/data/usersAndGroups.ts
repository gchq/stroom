import { loremIpsum } from "lorem-ipsum";
import { StroomUser } from "components/AuthorisationManager/api/userGroups";
import v4 from "uuid/v4";

export const adminUser: StroomUser = {
  uuid: v4(),
  name: "admin",
  group: false,
};

export const generateTestUser = (): StroomUser => ({
  uuid: v4(),
  name: loremIpsum({ count: 3, units: "words" }),
  group: false,
});

export const generateTestGroup = (): StroomUser => ({
  uuid: v4(),
  name: loremIpsum({ count: 3, units: "words" }),
  group: true,
});
