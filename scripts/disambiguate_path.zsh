#!/usr/bin/env zsh

setopt errexit nounset pipefail
fpath=("${0:A:h}/lib/functions" $fpath)
autoload -Uz $fpath[1]/*(:t)

# Check if in a git repository
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    say_error "$0: not a git repository" >&2
    exit 1
fi

local -a base_dir_opt
zparseopts -D -E b:=base_dir_opt

local base_dir="."
if (( ${#base_dir_opt} > 0 )); then
    base_dir="${base_dir_opt[2]}"
fi

if ! git -C "$base_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    say_error "$0: base directory '$base_dir' is not inside a git repository" >&2
    exit 1
fi

local -a target_paths
target_paths=("$@")

if (( ${#target_paths} == 0 )); then
    say_error "$0: invalid command-line arguments: no paths specified" >&2
    exit 2
fi

# Function to get matches relative to base_dir
get_matches() {
    local target_basename=$1
    local search_dir=$2
    
    # Get all files git knows about in search_dir, relative to search_dir
    local -a files
    files=(${(f)"$(git -C "$search_dir" ls-files --cached --others --exclude-standard)"})
    
    local -A seen_matches
    for f in $files; do
        local -a parts
        parts=(${(s:/:)f})
        local current_path=""
        for p in $parts; do
            if [[ -z "$current_path" ]]; then
                current_path="$p"
            else
                current_path="$current_path/$p"
            fi
            if [[ "$p" == "$target_basename" ]]; then
                seen_matches[$current_path]=1
            fi
        done
    done
    
    if (( ${#seen_matches} > 0 )); then
        print -l ${(k)seen_matches}
    fi
}

for target_path in $target_paths; do
    local target_basename="${target_path:t}"
    local -a matches
    matches=(${(f)"$(get_matches "$target_basename" "$base_dir")"})

    # Ensure target_path is in matches and at the front
    local -a filtered_matches
    filtered_matches=("$target_path")
    for m in $matches; do
        if [[ "$m" != "$target_path" ]]; then
            filtered_matches+=("$m")
        fi
    done


    
    # If zero or one match was found (initially), filtered_matches will have 
    # either just target_path or target_path + others.
    # Wait, the requirement says "If zero or one match is found, then the value of target_basename is printed"
    # This refers to the search results.
    if (( ${#matches} <= 1 )); then
        say "$target_basename"
        continue
    fi
    
    # Disambiguate
    local -a target_parts
    target_parts=(${(s:/:)target_path})
    local max_n=${#target_parts}
    
    local final_result=""
    for (( n=1; n <= max_n; n++ )); do
        local -A disambiguated
        disambiguated=()
        local -a current_strings
        current_strings=()
        local duplicate=0

        for m in $filtered_matches; do
            local -a m_parts
            m_parts=(${(s:/:)m})
            local start_idx=$(( ${#m_parts} - n + 1 ))
            if (( start_idx < 1 )); then
                start_idx=1
            fi
            local s="${(j:/:)m_parts[$start_idx,-1]}"
            if (( ${+disambiguated[$s]} )); then
                duplicate=1
            fi
            disambiguated[$s]=1
            current_strings+=("$s")
        done

        if (( duplicate == 0 )); then
            final_result="${current_strings[1]}"
            break
        fi

        if (( n == max_n )); then
            final_result="${current_strings[1]}"
        fi
    done

    
    say "$final_result"
done
