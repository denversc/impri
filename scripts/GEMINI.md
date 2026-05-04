# Zsh shell scripts

This directory contains helper zsh shell scripts.

## Coding standards

### Idiomatic zsh

The scripts in this directory **MUST** use idiomatic zsh scripting style,
without regard to portability to any other shells.
Namely, portability to bash is **NOT** a goal or even a consideration.

### Zsh script header

Every zsh script **MUST** begin with the following lines:

```
#!/usr/bin/env zsh

setopt errexit nounset pipefail
fpath=("${0:A:h}/lib/functions" $fpath)
autoload -Uz $fpath[1]/*(:t)
```

### Use `say` (and friends) instead of `echo`

The builtin `echo` command in zsh has all of the same historical problems as
that command in other posix shells, like bash.

Therefore, use one of the custom functions defined in list below instead of
`echo`, with `say` being the generally-preferred option if none of the other
suit. Assume that the requisite `fpath` manipulations and `autoload` commands
have been run, ensuring that these custom functions are available and ready to
use.

All of these functions write to stdout. In the case of an error message, write
to stderr instead using the normal shell redirection: `>&2`.

* `say` - prints the given arguments to the screen, separated by spaces.
* `sayn` - exactly the same as `say` but omits the trailing newline;
    this is useful to print chunks of a line one by one.
* `say_error` - prints a colorful error message with the given arguments.
* `say_args` - prints the arguments of a shell command with appropriate quoting
    in the presence of whitespace.

### Use `zparseopts` for argument parsing

Whenever a script or zsh function needs to parse its arguments, use the
`zparseopts` builtin. Namely, do **NOT** use `getopts` because it is inferior.

### Argument parsing error handling

If a _top-level zsh script_ encounters an error parsing the command-line
arguments, then it **MUST** print an error message to stderr and exit with an
exit code of 2, like this:

```
say_error "$0: invalid command-line arguments: [specific error message]" >&2
exit 2
```

If a _zsh function_ encounters an error parsing its arguments, then it **MUST**
print an error message to stderr and return 2, like this:

```
say_error "$0: invalid arguments: [specific error message]" >&2
return 2
```
