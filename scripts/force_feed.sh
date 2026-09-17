#!/usr/bin/env bash

# Script to force the value of the 'Feed' key in all
# .meta files within the supplied zip file.
# It will make a backup copy of the supplied zip file,
# extract it to a temporary dir, update the 'Feed' entries
# in any .meta files then zip all the files into the original
# zip file location.
set -e

main() {
  if [[ $# -ne 2 ]]; then
    echo "Invalid args!" 1>&2
    echo "Usage: force_feed.sh ZIP_FILE FEED_NAME" 1>&2
    exit 1
  fi
  local zip_file="$1"; shift
  local feed_name="$1"; shift
  local backup_zip_file="${zip_file}.old"

  if [[ ! -f "${zip_file}" ]]; then
    echo "File ${zip_file} doesn't exist" 1>&2
    exit 1
  fi
  
  tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}"/tmp.XXXXXXXX)

  #echo "Using tmp_dir: ${tmp_dir}"

  unzip -q -d "${tmp_dir}" "${zip_file}"

  echo "Creating backup file ${backup_zip_file}"

  mv "${zip_file}" "${backup_zip_file}"

  pushd "${tmp_dir}" > /dev/null

  find . -type f -name "*.meta" -print0 \
    | xargs -0 perl -pi -e "s/^Feed:.*/Feed:${feed_name}/g"

  zip -q -9 -r "${zip_file}" ./*

  popd > /dev/null

  rm -rf "${tmp_dir?"tmp_dir not set"}"

  echo "Updated ${zip_file}, set 'Feed:${feed_name}'"
}

main "$@"
