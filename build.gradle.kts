import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.RunConfigurationContainer
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.Charset

plugins {
  id("java-library")
  id("maven-publish")
  id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
  id("eclipse")
  id("com.gtnewhorizons.retrofuturagradle") version "1.4.0"
}

// Project properties
group = "lyl.emc1"
version = "1.0.0"

// Set the toolchain version to decouple the Java we run Gradle with from the Java used to compile and run the mod
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(8))
    // Azul covers the most platforms for Java 8 toolchains, crucially including MacOS arm64
    vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.AZUL)
  }
  // Generate sources and javadocs jars when building and publishing
  withSourcesJar()
  withJavadocJar()
}

tasks.javadoc {
    options.encoding = "UTF-8"
    isFailOnError = false
}

// 4. 强制UTF-8编译（Gradle 1.12 Kotlin DSL兼容写法）
tasks.getByName<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.compilerArgs = listOf("-encoding", "UTF-8")
}

// 无需额外导入（用完整包名适配Gradle 1.12）
tasks.getByName<JavaExec>("runClient") {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
}


tasks.getByName<JavaExec>("runServer") {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
}

// // 6. 资源打包强制UTF-8
tasks.getByName<ProcessResources>("processResources") {
    filteringCharset = "UTF-8"
}

// Most RFG configuration lives here, see the JavaDoc for com.gtnewhorizons.retrofuturagradle.MinecraftExtension
minecraft {
  mcVersion.set("1.7.10")

  // Username for client run configurations
  username.set("Developer")

  // Generate a field named VERSION with the mod version in the injected Tags class
  injectedTags.put("VERSION", project.version)

  // If you need the old replaceIn mechanism, prefer the injectTags task because it doesn't inject a javac plugin.
  // tagReplacementFiles.add("RfgExampleMod.java")

  // Enable assertions in the mod's package when running the client or server
  extraRunJvmArguments.add("-ea:${project.group}")

  // If needed, add extra tweaker classes like for mixins.
  // extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")

  // Exclude some Maven dependency groups from being automatically included in the reobfuscated runs
  groupsToExcludeFromAutoReobfMapping.addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft")
}

// Generates a class named rfg.examplemod.Tags with the mod version in it, you can find it at
tasks.injectTags.configure {
  outputClassName.set("${project.group}.Tags")
}

// Put the version from gradle into mcmod.info
tasks.processResources.configure {
  val projVersion = project.version.toString() // Needed for configuration cache to work
  inputs.property("version", projVersion)

  filesMatching("mcmod.info") {
    expand(mapOf("modVersion" to projVersion))
  }
}

// Create a new dependency type for runtime-only dependencies that don't get included in the maven publication
val runtimeOnlyNonPublishable: Configuration by configurations.creating {
  description = "Runtime only dependencies that are not published alongside the jar"
  isCanBeConsumed = false
  isCanBeResolved = false
}
listOf(configurations.runtimeClasspath, configurations.testRuntimeClasspath).forEach {
  it.configure {
    extendsFrom(
      runtimeOnlyNonPublishable
    )
  }
}

// Add an access tranformer
// tasks.deobfuscateMergedJarToSrg.configure {accessTransformerFiles.from("src/main/resources/META-INF/mymod_at.cfg")}

// Dependencies
repositories {
  maven {
    name = "OvermindDL1 Maven"
    url = uri("https://gregtech.overminddl1.com/")
  }
  maven {
    name = "GTNH Maven"
    url = uri("https://nexus.gtnewhorizons.com/repository/public/")
  }
}


dependencies {
  // Adds NotEnoughItems and its dependencies (CCL&CCC) to runClient/runServer
  runtimeOnlyNonPublishable("com.github.GTNewHorizons:NotEnoughItems:2.3.39-GTNH:dev")

  // 只用于编译，你的 mod 不会把 ProjectE 打进最终 jar
  compileOnly(rfg.deobf(files("libs/ProjectE-1.7.10-PE1.10.1.jar")))
  // 游戏运行时需要加载
  runtimeOnly(rfg.deobf(files("libs/ProjectE-1.7.10-PE1.10.1.jar")))


  // Example: grab the ic2 jar from curse maven and deobfuscate
  // api(rfg.deobf("curse.maven:ic2-242638:2353971"))
  // Example: grab the ic2 jar from libs/ in the workspace and deobfuscate
  // api(rfg.deobf(project.files("libs/ic2.jar")))
}

// Publishing to a Maven repository
publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
  repositories {
    // Example: publishing to the GTNH Maven repository
    maven {
      url = uri("https://nexus.gtnewhorizons.com/repository/releases/")
      credentials {
        username = System.getenv("MAVEN_USER") ?: "NONE"
        password = System.getenv("MAVEN_PASSWORD") ?: "NONE"
      }
    }
  }
}

// IDE Settings
eclipse {
  classpath {
    isDownloadSources = true
    isDownloadJavadoc = true
  }
}

idea {
  module {
    isDownloadJavadoc = true
    isDownloadSources = true
    inheritOutputDirs = true // Fix resources in IJ-Native runs
  }
  project {
    this.withGroovyBuilder {
      "settings" {
        "runConfigurations" {
          val self = this.delegate as RunConfigurationContainer
          self.add(Gradle("1. Run Client").apply {
            setProperty("taskNames", listOf("runClient"))
          })
          self.add(Gradle("2. Run Server").apply {
            setProperty("taskNames", listOf("runServer"))
          })
          self.add(Gradle("3. Run Obfuscated Client").apply {
            setProperty("taskNames", listOf("runObfClient"))
          })
          self.add(Gradle("4. Run Obfuscated Server").apply {
            setProperty("taskNames", listOf("runObfServer"))
          })
          /*
          These require extra configuration in IntelliJ, so are not enabled by default
          self.add(Application("Run Client (IJ Native, Deprecated)", project).apply {
            mainClass = "GradleStart"
            moduleName = project.name + ".ideVirtualMain"
            afterEvaluate {
              val runClient = tasks.runClient.get()
              workingDirectory = runClient.workingDir.absolutePath
              programParameters = runClient.calculateArgs(project).map { '"' + it + '"' }.joinToString(" ")
              jvmArgs = runClient.calculateJvmArgs(project).map { '"' + it + '"' }.joinToString(" ") +
                ' ' + runClient.systemProperties.map { "\"-D" + it.key + '=' + it.value.toString() + '"' }
                .joinToString(" ")
            }
          })
          self.add(Application("Run Server (IJ Native, Deprecated)", project).apply {
            mainClass = "GradleStartServer"
            moduleName = project.name + ".ideVirtualMain"
            afterEvaluate {
              val runServer = tasks.runServer.get()
              workingDirectory = runServer.workingDir.absolutePath
              programParameters = runServer.calculateArgs(project).map { '"' + it + '"' }.joinToString(" ")
              jvmArgs = runServer.calculateJvmArgs(project).map { '"' + it + '"' }.joinToString(" ") +
                ' ' + runServer.systemProperties.map { "\"-D" + it.key + '=' + it.value.toString() + '"' }
                .joinToString(" ")
            }
          })
          */
        }
        "compiler" {
          val self = this.delegate as org.jetbrains.gradle.ext.IdeaCompilerConfiguration
          afterEvaluate {
            self.javac.moduleJavacAdditionalOptions = mapOf(
              (project.name + ".main") to
                tasks.compileJava.get().options.compilerArgs.map { '"' + it + '"' }.joinToString(" ")
            )
          }
        }
      }
    }
  }
}

tasks.processIdeaSettings.configure {
  dependsOn(tasks.injectTags)
}
