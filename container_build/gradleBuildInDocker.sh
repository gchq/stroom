#!/bin/bash

user_id=
user_id="$(id -u)"

group_id=
group_id="$(id -g)"

image_tag="stroom-builder"

host_abs_repo_dir="/home/dev/git_work/v7stroom"
dest_dir="/builder/shared"

echo -e "${GREEN}User ID ${user_id}${NC}"
echo -e "${GREEN}Group ID ${group_id}${NC}"

# Create a persistent vol for the home dir, idempotent
docker volume create builder-home-dir-vol

docker build -t "${image_tag}" \
  --build-arg "USER_ID=${user_id}" \
  --build-arg "GROUP_ID=${group_id}" ./docker_java

# Mount the whole repo into the container so we can run the build
docker run \
  --interactive \
  --tty \
  --rm \
  --mount "type=bind,src=${host_abs_repo_dir},dst=${dest_dir}" \
  --volume builder-home-dir-vol:/home/builder \
  --workdir "${dest_dir}" \
  --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --name "stroom-builder" \
  "${image_tag}" \
  bash
  #bash -c './yarnBuild.sh'
  #bash -c 'echo $PWD; nvm --version; node --version; npm --version; npx --version; yarn --version; ./yarnBuild.sh'


  #--mount "type=bind,src=$HOME/.gradle,dst=/home/builder/.gradle" \
  #--mount "type=bind,src=$HOME/.m2,dst=/home/builder/.m2" \
