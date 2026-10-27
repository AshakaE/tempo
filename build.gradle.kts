plugins {
  kotlin("jvm") version "2.2.21"
}

repositories { mavenCentral() }

dependencies { testImplementation(kotlin("test")) }

// Target a conservative bytecode level so any JDK 17+ can build and run the tests.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

// Compile the assessment file where it lives instead of moving it into src/test/kotlin.
sourceSets.test {
  kotlin.srcDir(projectDir)
  kotlin.include("Take Home Assessment - Hierarchy.kt")
}

tasks.test {
  useJUnitPlatform()
  testLogging { events("passed", "failed", "skipped") }
}
