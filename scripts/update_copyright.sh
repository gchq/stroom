#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" || "${1:-}" == "-d" ]]; then
  DRY_RUN=true
  echo "=== DRY RUN MODE: Displaying planned changes only ==="
  echo ""
fi

# Track how many files need updates
#changed_count=0

git ls-files '*.java' | while IFS= read -r file; do

  if [[ "${file}" =~ /jooq/ ]]; then
    echo "Skipping JOOQ $file"
    continue
  fi

  if grep -sF "@Generated(" "${file}"; then
    echo "Skipping JOOQ $file"
    continue
  fi

  # Get the year of the first commit for the file
  #first_year=$(git log --follow --reverse --format="%ad" --date=format:"%Y" -- "$file" | head -n 1)
  first_year=$(git log --follow --format="%ad" --date=format:"%Y" -- "$file" | tail -n 1)

  # Fall back to current year if untracked/no commit history
  if [ -z "$first_year" ]; then
    first_year=$(date +%Y)
  fi


  # Find the existing copyright line matching the target pattern
  #existing_line=$(perl -ne 'print if /\s\*\s+Copyright\s+\d{4}(-\d{4})?\s+Crown Copyright/' "$file" | head -n 1)
  existing_line=$(perl -ne 'print if /\s+\*\s+Copyright\s+\d{4}.*\s+Crown Copyright/' "$file" | head -n 1)

  if [ -n "$existing_line" ]; then
    # Generate the expected target line
    #target_line=$(echo "$existing_line" | perl -pe "s/(\s\*\s+Copyright\s+)\d{4}(-\d{4})?(\s+Crown Copyright)/\$1${first_year}\$3/g")
    target_line=" * Copyright ${first_year} Crown Copyright"

    # Check if the file needs an update
    if [ "$existing_line" != "$target_line" ]; then
      if [ "$DRY_RUN" = true ]; then
        echo "$file - ${first_year}"
        echo "  OLD: $existing_line"
        echo "  NEW: $target_line"
        echo ""
      else
        perl -pi -e "s/(\s\*\s+Copyright\s+)\d{4}(-\d{4})?(\s+Crown Copyright)/\$1${first_year}\$3/g" "$file"
        echo "Updated: $file - ${first_year}"
      fi
    fi
  else
    echo "$file - MISSING"
  fi
done
