#!/bin/bash

user_id=
user_id="$(id -u)"

group_id=
group_id="$(id -g)"

image_tag="stroom-ui-builder"

dest_dir="/builder/shared"

echo -e "${GREEN}User ID ${user_id}${NC}"
echo -e "${GREEN}Group ID ${group_id}${NC}"

docker build -t "${image_tag}" \
  --build-arg "USER_ID=${user_id}" \
  --build-arg "GROUP_ID=${group_id}" ./docker_build_container

docker run -it --rm \
  --mount "type=bind,src=$(pwd),dst=${dest_dir}" \
  --workdir "${dest_dir}" \
  "${image_tag}" \
  #bash
  bash -c 'echo $PWD; nvm --version; node --version; npm --version; npx --version; yarn --version; ./yarnBuild.sh'
