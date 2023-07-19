import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
  `java-library`
}

dependencies {
  implementation(libs.arrowCore)
  implementation(libs.bouncyCastle)
  implementation(libs.bitcoinj)
  implementation(libs.okio)
  implementation(libs.quiver)
  implementation(libs.slf4jApi)

  testImplementation(libs.guavaJre)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestExtAssertionsArrow)
  testImplementation(libs.kotestExtPropertyArrow)
  testImplementation(libs.kotestFrameworkApi)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.kotestProperty)

  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.slf4jSimple)

  apply(plugin = libs.plugins.dokka.get().pluginId)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

tasks.withType<DokkaTask>().configureEach {
  dokkaSourceSets {
    named("main") {
      moduleName.set("LN-Invoice")

      // Includes custom documentation
      includes.from("module.md")

      // Points source links to GitHub
      sourceLink {
        localDirectory.set(file("src/main/kotlin"))
        remoteUrl.set(URL("https://github.com/cashapp/ln-invoice/tree/master/lib/src/main/kotlin"))
        remoteLineSuffix.set("#L")
      }
    }
  }
}

