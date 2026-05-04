# Impri Label Printer Android Application

This git repository contains an Android application called "Impri" which
connects to consumer Brother label makers via Bluetooth to print labels.

This application is an alternative to the official, proprietary Android
application from Brother. This application focuses on a faster application with
improved user experience and better features.

## Code Compilation

After completing a task that involves changes to any files in the "src" directory
you **MUST** verify that the code compiles by running
`./gradlew compileDebugSources`.

Prefer running the `compileDebugSources` task over the more conventional
`assembleDebug` task because the latter does a whole bunch of extra work beyond
just verifying that the code compiles, such as bundling the application into an
APK file.

## Code Formatting

After completing a task that involves changes to any files that are formatted by
the "spotless" plugin as configured in ./build.gradle.kts you **MUST** run
`./gradlew spotlessApply` to format the code.

## Git Information

### Staging and committing policy

**DO NOT** stage or commit changes to git unless explicitly asked to do so.
By default, all modifications must be left unstaged in the working directory.

### Commit requirements

Refer to `docs/git_commit.md` to understand how the requirements of running
`git commit`, especially the expected commit message format.
