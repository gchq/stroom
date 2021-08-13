import { loremIpsum } from "lorem-ipsum";
import { ApiKey } from "api/stroom";
import { now } from "moment";

const lr = (count: number) => loremIpsum({ count, units: "words" });
const getToken = (): ApiKey => {
  return {
    id: Math.floor(Math.random() * 1000),
    version: 1,
    createTimeMs: now(),
    createUser: lr(1),
    updateTimeMs: now(),
    updateUser: lr(1),
    userId: lr(1),
    type: "user",
    data: lr(1),
    expiresOnMs: now(),
    comments: lr(1),
    enabled: true,
  };
};

const newToken = getToken();

// const wellUsedUser = getUser();
// wellUsedUser.forcePasswordChange = false;
// wellUsedUser.loginCount = 99;
// wellUsedUser.updateTimeMs = now();
// wellUsedUser.updateUser = lr(1);
// wellUsedUser.lastLoginMs = now();
//
// const disabledUser = getUser();
// disabledUser.enabled = false;
//
// const inactiveUser = getUser();
// inactiveUser.inactive = true;
//
// const lockedUser = getUser();
// lockedUser.locked = true;
//newUser, wellUsedUser, disabledUser, inactiveUser, lockedUser };
export { newToken };
