import React from 'react';
import PropTypes from 'prop-types';

import { Dropdown, Menu } from 'semantic-ui-react';

const AppMenu = props => (
  <Menu>
    <Dropdown item text="Item">
      <Dropdown.Menu>
        <Dropdown.Item>New</Dropdown.Item>
        <Dropdown.Divider />
        <Dropdown.Item>Close</Dropdown.Item>
        <Dropdown.Item>Close All</Dropdown.Item>
        <Dropdown.Divider />
        <Dropdown.Item>Save</Dropdown.Item>
        <Dropdown.Item>Save All</Dropdown.Item>
        <Dropdown.Divider />
        <Dropdown.Item>Info</Dropdown.Item>
        <Dropdown.Item>Copy</Dropdown.Item>
        <Dropdown.Item>Move</Dropdown.Item>
        <Dropdown.Item>Rename</Dropdown.Item>
        <Dropdown.Item>Delete</Dropdown.Item>
        <Dropdown.Divider />
        <Dropdown.Item>Permissions</Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>
    <Dropdown item text="Tools">
      <Dropdown.Menu>
        <Dropdown.Item>User Permissions</Dropdown.Item>
        <Dropdown.Item>Volumes</Dropdown.Item>
        <Dropdown.Item>API Keys</Dropdown.Item>
        <Dropdown.Item>Stream Tasks</Dropdown.Item>
        <Dropdown.Item>Users</Dropdown.Item>
        <Dropdown.Item>Elastic Search</Dropdown.Item>
        <Dropdown.Item>Data Retention</Dropdown.Item>
        <Dropdown.Item>Properties</Dropdown.Item>
        <Dropdown.Divider />
        <Dropdown.Item>Import</Dropdown.Item>
        <Dropdown.Item>Export</Dropdown.Item>
        <Dropdown.Item>Dependencies</Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>

    <Dropdown item text="Monitoring">
      <Dropdown.Menu>
        <Dropdown.Item>Database Tables</Dropdown.Item>
        <Dropdown.Item>Jobs</Dropdown.Item>
        <Dropdown.Item>Nodes</Dropdown.Item>
        <Dropdown.Item>Caches</Dropdown.Item>
        <Dropdown.Item>Server Tasks</Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>

    <Dropdown item text="User">
      <Dropdown.Menu>
        <Dropdown.Item>Logout</Dropdown.Item>
        <Dropdown.Item>Change Password</Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>

    <Dropdown item text="Help">
      <Dropdown.Menu>
        <Dropdown.Item>Help</Dropdown.Item>
        <Dropdown.Item>About</Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>
  </Menu>
);

export default AppMenu;
