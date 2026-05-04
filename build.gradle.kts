import com.diffplug.gradle.spotless.SpotlessPlugin
import com.diffplug.spotless.LineEnding

plugins {
  alias(libs.plugins.spotless)
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}

apply<SpotlessPlugin>()

spotless {
  kotlin {
    target("**/*.kt")
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    lineEndings = LineEnding.UNIX
    endWithNewline()
    trimTrailingWhitespace()
  }
  kotlinGradle {
    target("*/**.gradle.kts")
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    lineEndings = LineEnding.UNIX
    endWithNewline()
    trimTrailingWhitespace()
  }
  flexmark {
    target("**/*.md")
    targetExclude("**/third_party/**", "src/test/resources/**", "release_report.md")
    flexmark(libs.versions.flexmark.get())
    lineEndings = LineEnding.UNIX
    endWithNewline()
    trimTrailingWhitespace()
  }
}
