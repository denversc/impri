# Git commit requirements

This file documents the requirements that AI agents **MUST** follow when running
`git commit` on this repository.

## Git Commit Message Specification

When creating git commit messages in this repository, the following format MUST be strictly adhered to:

1. **Title Line:** The first line of the commit message must begin with the name of the most prominent file that has changes, with its full path relative to the repository root, followed by a colon (`:`), followed by a single sentence that describes the changes. There is no line length restriction on this first line.
   * If there are multiple files with changes that are all roughly equally "prominent" then simply omit the paths to the files and use a high-level grouping, such as possibly a wildcard (e.g. *gradle.kts) or just the directory (e.g. "src/main").
   * Example: `MainActivity.kt: add bluetooth permission check on startup.`
2. **Detailed Description (Optional):** If there are more relevant details that cannot be captured in the single sentence title, add a blank line followed by a paragraph describing the changes.
3. **Wrapping:** The detailed description paragraph MUST wrap at a maximum of 100 characters per line.

## Example

```text
MainActivity.kt: add bluetooth permission check on startup.

The app now explicitly requests BLUETOOTH_CONNECT and BLUETOOTH_SCAN permissions from the user
when the MainActivity is created. If the permissions are denied, an error dialog is shown to the
user explaining that the permissions are required to connect to the label maker.
```
