#!/bin/bash

echo -e "\n${GREEN}Running Yarn install${NC}"
yarn install

echo -e "\n${GREEN}Running Yarn build${NC}"
yarn build
