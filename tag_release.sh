#!/usr/bin/env bash
set -e

setup_echo_colours() {

    #Shell Colour constants for use in 'echo -e'
    # shellcheck disable=SC2034
    {
        RED='\033[1;31m'
        GREEN='\033[1;32m'
        YELLOW='\033[1;33m'
        BLUE='\033[1;34m'
        LGREY='\e[37m'
        DGREY='\e[90m'
        NC='\033[0m' # No Color
    }
}

main() {
    setup_echo_colours
    echo

    local -r changelog_file="CHANGELOG.md"

    if [ $# -ne 1 ]; then
        echo -e "${RED}ERROR${GREEN}: Missing version argument${NC}"
        echo -e "${GREEN}Usage: ${BLUE}./tag_release.sh version${NC}"
        echo -e "${GREEN}e.g:   ${BLUE}./tag_release.sh v6.0-beta.17${NC}"
        echo
        echo -e "${GREEN}This script will extract the changes from the ${BLUE}CHANGELOG.md${GREEN} file for the passed${NC}"
        echo -e "${GREEN}version tag and create an annotatated git commit with it. The tag commit will be pushed${NC}"
        echo -e "${GREEN}to the origin.${NC}"
        exit 1
    fi

    local version=$1

    if [ ! -f "${changelog_file}" ]; then
        echo -e "${RED}ERROR${GREEN}: The file ${BLUE}${changelog_file}${NC} does not exist in the current directory.${NC}"
        echo
        exit 1
    fi

    if ! git rev-parse --show-toplevel > /dev/null 2>&1; then
        echo -e "${RED}ERROR${GREEN}: You are not in a git repository. This script should be run from the root of a repository.${NC}"
        echo
        exit 1
    fi

    if git tag | grep -q "^${version}$"; then
        echo -e "${RED}ERROR${GREEN}: This repository has already been tagged with [${BLUE}${version}${GREEN}].${NC}"
        echo
        exit 1
    fi

    if ! grep -q "^\s*##\s*\[${version}\]" "${changelog_file}"; then
        echo -e "${RED}ERROR${GREEN}: Version [${BLUE}${version}${GREEN}] is not in the CHANGELOG.${NC}"
        echo
        exit 1
    fi

    if ! grep -q "^\[${version}\]:" "${changelog_file}"; then
        echo -e "${RED}ERROR${GREEN}: Version [${BLUE}${version}${GREEN}] does not have a link entry at the bottom of the CHANGELOG.${NC}"
        echo -e "${GREEN}e.g ${BLUE}[v6.0-beta.17]: https://github.com/gchq/stroom/compare/v6.0-beta.16...v6.0-beta.17${NC}"
        echo
        exit 1
    fi

    if [ "$(git status --porcelain 2>/dev/null | wc -l)" -ne 0 ]; then
        echo -e "${RED}ERROR${GREEN}: There are uncommited changes or untracked files. Commit them before tagging.${NC}"
        echo
        exit 1
    fi

    local change_text
    # delete all lines upto and including the desired version header
    # then output all lines untill quitting when you hit the next 
    # version header
    change_text="$(sed "1,/^\s*##\s*\[${version}\]/d;/## \[/Q" "${changelog_file}")"

    # Add the release version as the top line of the commit msg, followed by
    # two new lines
    change_text="${version}\n\n${change_text}"

    # Remove any repeated blank lines with cat -s
    change_text="$(echo -e "${change_text}" | cat -s)"

    echo -e "${GREEN}You are about to create the git tag ${BLUE}${version}${GREEN} with the following commit message.${NC}"
    echo -e "${GREEN}If this is pretty empty then you need to add some entries to the ${BLUE}${changelog_file}${NC}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "${YELLOW}${change_text}${NC}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"

    read -rsp $'Press "y" to continue, any other key to cancel.\n' -n1 keyPressed

    if [ "$keyPressed" = 'y' ] || [ "$keyPressed" = 'Y' ]; then
        echo
        echo -e "${GREEN}Tagging the current commit${NC}"
        echo -e "${change_text}" | git tag -a --file - "${version}"

        echo -e "${GREEN}Pushing the new tag${NC}"
        git push origin "${version}"

        echo -e "${GREEN}Done.${NC}"
        echo
    else
        echo
        echo "${GREEN}Exiting with tagging${NC}"
        echo
        exit 0
    fi
}

main "$@"
