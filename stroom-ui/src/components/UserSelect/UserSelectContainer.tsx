/*
 * Copyright 2019 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import * as Fuse from "fuse.js";
import { User } from "components/users/types";
import UserSelect from "./UserSelect";
import useUserSearch from "components/users/SearchUsers/useUserSearch";

interface Props {
  onChange: (user: string) => void;
  fuzzy?: boolean;
}

const UserSelectContainer: React.FunctionComponent<Props> = ({
  onChange,
  fuzzy,
}) => {
  const { users: initialUsers } = useUserSearch();
  const [users, setUsers] = React.useState<User[]>(initialUsers);

  // initialUsers is [] on first render. So when the values finally
  // arrive from the api we need to setUsers.
  React.useEffect(() => {
    setUsers(initialUsers);
  }, [initialUsers]);

  const handleSearch = React.useCallback(
    (criteria: string) => {
      // If we don't have any critera we want to show all users in the drop-down.
      if (criteria.length === 0) {
        setUsers(initialUsers);
      } else {
        let searchResults = [];
        if (fuzzy) {
          var fuse = new Fuse(initialUsers, {
            keys: [{ name: "email", weight: 1 }],
          });
          searchResults = fuse.search(criteria);
        } else {
          searchResults = initialUsers.filter(user =>
            user.email.includes(criteria),
          );
        }
        setUsers(searchResults);
      }
    },
    [setUsers, fuzzy, initialUsers],
  );

  return (
    <UserSelect options={users} onChange={onChange} onSearch={handleSearch} />
  );
};

export default UserSelectContainer;
