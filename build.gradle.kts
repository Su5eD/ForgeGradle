import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.ow2.asm:asm:6.2.1")
        classpath("org.ow2.asm:asm-tree:6.2.1")
    }
}

plugins {
    java
    idea
    eclipse
    `maven-publish`
    id("com.gradle.plugin-publish").version("0.14.0")
    id("com.github.hierynomus.license").version("0.15.0")
}

group = "net.minecraftforge.gradle"
version = "2.3.2"
java.targetCompatibility = JavaVersion.VERSION_1_8
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    maven {
        // because Srg2Source needs an eclipse dependency.
        name = "eclipse"
        url = uri("https://repo.eclipse.org/content/groups/eclipse/")
    }
    jcenter() // get as many deps from here as possible
    mavenCentral()

    // because SS and its snapshot
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }


    // because of the GradleStart stuff
    maven {
        name = "mojang"
        url = uri("https://libraries.minecraft.net/")
    }
    maven {
        name = "forge"
        url = uri("https://maven.minecraftforge.net/")
    }
}

configurations {
    create("deployerJars")
    create("shade")
    compileOnly.get().extendsFrom(getByName("shade"))
            all {
                resolutionStrategy {
                    force("org.ow2.asm:asm-commons:6.0", "org.ow2.asm:asm-tree:6.0", "org.ow2.asm:asm:6.0")
                }
            }
}

dependencies {
    implementation(gradleApi())

    // moved to the beginning to be the overrider
    //compile 'org.ow2.asm:asm-debug-all:6.0'
    implementation("com.google.guava:guava:18.0")

    implementation("net.sf.opencsv:opencsv:2.3") // reading CSVs.. also used by SpecialSource
    implementation("com.cloudbees:diff4j:1.1") // for difing and patching
    implementation("com.github.abrarsyed.jastyle:jAstyle:1.3") // formatting
    implementation("net.sf.trove4j:trove4j:2.1.0") // because its awesome.

    implementation("com.github.jponge:lzma-java:1.3") // replaces the LZMA binary
    implementation("com.nothome:javaxdelta:2.0.1") // GDIFF implementation for BinPatches
    implementation("com.google.code.gson:gson:2.2.4") // Used instead of Argo for buuilding changelog.
    implementation("com.github.tony19:named-regexp:0.2.3") // 1.7 Named regexp features
    implementation("net.minecraftforge:forgeflower:1.0.342-SNAPSHOT") // Fernflower Forge edition

    "shade"("net.md-5:SpecialSource:1.8.2") // deobf and reobf

    // because curse
    implementation("org.apache.httpcomponents:httpclient:4.3.3")
    implementation("org.apache.httpcomponents:httpmime:4.3.3")

    // mcp stuff
    "shade"("de.oceanlabs.mcp:RetroGuard:3.6.6")
    "shade"("de.oceanlabs.mcp:mcinjector:3.4-SNAPSHOT") {
        exclude(group = "org.ow2.asm")
    }
    "shade"("net.minecraftforge:Srg2Source:5.0.+") {
        exclude(group = "org.ow2.asm")
        exclude(group = "org.eclipse.equinox", module = "org.eclipse.equinox.common")
        exclude(group = "cpw.mods", module = "modlauncher")
    }

    //Stuff used in the GradleStart classes
    compileOnly("com.mojang:authlib:1.5.16")
    compileOnly("net.minecraft:launchwrapper:1.11") {
        exclude(group = "org.ow2.asm")
    }

    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.1.3-2")
    testImplementation("junit:junit:4.12")
}

sourceSets {
    val conf = configurations["shade"]
    main.get().compileClasspath += conf
    main.get().runtimeClasspath += conf
    test.get().compileClasspath += conf
    test.get().runtimeClasspath += conf
}

tasks {
    named<JavaCompile>("compileJava") {
        options.isDeprecation = true
    }

    named<ProcessResources>("processResources") {
        val main = sourceSets.main.get()

        from(main.resources.srcDirs) {
            include("forgegradle.version.txt")
            expand("version" to project.version)
        }
        from(main.resources.srcDirs) {
            exclude("forgegradle.version.txt")
        }
    }

    register<PatchJDTClasses>("patchJDT") {
        target(PatchJDTClasses.compilationUnitResolver)
        target(PatchJDTClasses.rangeExtractor)
        configurations["shade"].resolvedConfiguration.resolvedArtifacts.stream().filter { dep ->
            dep.name == "org.eclipse.jdt.core" || dep.name == "Srg2Source"
        }.forEach { dep ->
            library(dep.file)
        }

        output = file("build/patchJDT/patch_jdt.jar")
    }

    named<Jar>("jar") {
        dependsOn("patchJDT")

        configurations["shade"].forEach { dep ->
            /* I can use this again to find where dupes come from, so.. gunna just keep it here.
            logger.lifecycle(dep.toString())
            project.zipTree(dep).visit {
                element ->
                    def path = element.relativePath.toString()
                    if (path.contains('org/eclipse/core') && path.endsWith('.class'))
                        println "  $element.relativePath"

            }
            */
            from(project.zipTree(dep)){
                exclude("META-INF", "META-INF/**", ".api_description", ".options", "about.html", "module-info.class", "plugin.properties", "plugin.xml", "about_files/**")
                duplicatesStrategy = DuplicatesStrategy.WARN
            }
        }

        from(zipTree(named<PatchJDTClasses>("patchJDT").get().output)){
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        manifest {
            attributes(
                "version" to project.version,
                "javaCompliance" to project.java.targetCompatibility,
                "group" to project.group,
                "Implementation-Version" to (project.version as String + getGitHash())
            )
        }
    }

    named<Javadoc>("javadoc") {
        classpath += configurations.compileOnly

        // linked javadoc urls.. why not...
        val opts = options as CoreJavadocOptions
        opts.addStringOption("link", "https://gradle.org/docs/current/javadoc/")
        opts.addStringOption("link", "https://guava.dev/releases/18.0/api/docs/")
        opts.addStringOption("link", "https://asm.ow2.org/asm50/javadoc/user/")
    }

    register<Jar>("javadocJar") {
        dependsOn(javadoc)
        from(javadoc)
        archiveClassifier.set("javadoc")
    }

    named<Test>("test") {
        if (project.hasProperty("filesmaven")) // disable this test when on the forge jenkins
            exclude("**/ExtensionMcpMappingTest*")
    }
}

artifacts {
    archives(tasks.named<Jar>("jar"))
    //archives javadocJar
}

license {
    ext {
        set("description", "A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.")
        set("year", "2013-2019")
        set("fullname", "Minecraft Forge")
    }
    header = rootProject.file("HEADER")
    include("**net/minecraftforge/gradle/**/*.java")
    excludes (
        mutableSetOf(
            "**net/minecraftforge/gradle/util/ZipFileTree.java",
            "**net/minecraftforge/gradle/util/json/version/*",
            "**net/minecraftforge/gradle/util/patching/Base64.java",
            "**net/minecraftforge/gradle/util/patching/ContextualPatch.java"
        )
    )
    ignoreFailures = false
    strictCheck = true
    mapping(mapOf("java" to "SLASHSTAR_STYLE"))
}

pluginBundle {
    website = "https://www.gradle.org/"
    vcsUrl = "https://github.com/MinecraftForge/ForgeGradle"
    description = "Gradle plugin for all Minecraft mod development needs"
    tags = setOf("forge", "minecraft", "minecraftforge", "sponge", "mcp")

    plugins {
        register("patcher") {
            id = "net.minecraftforge.gradle.patcher"
            displayName = "Minecraft Patcher Plugin"
        }
        register("tweakerClient") {
            id = "net.minecraftforge.gradle.tweaker-client"
            displayName = "Minecraft Client Tweaker Plugin"
        }
        register("tweakerServer") {
            id = "net.minecraftforge.gradle.tweaker-server"
            displayName = "Minecraft Server Tweaker Plugin"
        }
        register("forge") {
            id = "net.minecraftforge.gradle.forge"
            displayName = "MinecraftForge Mod Development Plugin"
        }
        register("launch4j") {
            id = "net.minecraftforge.gradle.launch4j"
            displayName = "Specialized Launch4J Gradle Plugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group as String
            version = project.version as String
            artifactId = project.name

            from(components["java"])

            pom {
                name.set(project.name)
                description.set("A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.")
                url.set("https://github.com/MinecraftForge/ForgeGradle")

                licenses {
                    license {
                        name.set("Lesser GNU Public License, Version 2.1")
                        url.set("https://www.gnu.org/licenses/lgpl-2.1.html")
                    }
                }

                developers {
                    developer {
                        id.set("AbrarSyed")
                        name.set("Abrar Syed")
                        roles.set(setOf("developer"))
                    }
                    developer {
                        id.set("LexManos")
                        name.set("Lex Manos")
                        roles.set(setOf("developer"))
                    }
                }

                scm {
                    url.set("https://github.com/MinecraftForge/ForgeGradle")
                    connection.set("scm:git:git://github.com/MinecraftForge/ForgeGradle.git")
                    developerConnection.set("scm:git:git@github.com:MinecraftForge/ForgeGradle.git")
                }

                issueManagement {
                    system.set("github")
                    url.set("https://github.com/MinecraftForge/ForgeGradle/issues")
                }
            }
        }
    }
}

// write out version so its convenient for doc deployment
file("build").mkdirs()
file("build/version.txt").writeText(version as String)

fun getGitHash(): String {
    val stdout = ByteArrayOutputStream()
    val process = exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
        isIgnoreExitValue = true
        this.errorOutput = ByteArrayOutputStream()
    }
    return "-" + if (process.exitValue != 0) "unknown" else stdout.toString(Charsets.UTF_8.name()).trim()
}

//TODO: Eclipse complains about unused messages. Find a way to make it shut up.
open class PatchJDTClasses: DefaultTask() {
    companion object {
        val compilationUnitResolver = "org/eclipse/jdt/core/dom/CompilationUnitResolver"
        val rangeExtractor = "net/minecraftforge/srg2source/ast/RangeExtractor"
        val resolveMethod = "resolve([Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Lorg/eclipse/jdt/core/dom/FileASTRequestor;ILjava/util/Map;I)V"
        val getContents = "org/eclipse/jdt/internal/compiler/util/Util.getFileCharContent(Ljava/io/File;Ljava/lang/String;)[C"
        val hookDescResolve = "(Ljava/lang/String;Ljava/lang/String;)[C"
    }

    @Input
    val targets: MutableSet<String> = HashSet()
    @Input
    val libraries: MutableSet<File> = HashSet()
    @OutputFile
    lateinit var output: File

    fun target(value: String) {
        targets.add(value)
    }

    fun library(value: File) {
        libraries.add(value)
    }

    @TaskAction
    fun patchClass() {
        val toProcess: MutableSet<String> = targets.toMutableSet()
        ZipOutputStream(FileOutputStream(output!!)).use { zout ->
            libraries.stream().filter{ !it.isDirectory }.forEach { lib ->
                ZipFile(lib).use useZip@ { zin ->
                    val remove: MutableSet<String> = HashSet()
                    toProcess.forEach { target ->
                        val entry = zin.getEntry("$target.class") ?: return@useZip

                        val node = ClassNode()
                        val reader = ClassReader(zin.getInputStream(entry))
                        reader.accept(node, 0)

                        //CompilationUnitResolver allows batch compiling, the problem is it is hardcoded to read the contents from a File.
                        //So we patch this call to redirect to us, so we can get the contents from our InputSupplier
                        if (compilationUnitResolver == target) {
                            logger.lifecycle("Transforming: $target From: $lib")
                            val resolve = node.methods.find { resolveMethod == it.name + it.desc } ?: throw RuntimeException("Failed to patch $target: Could not find method $resolveMethod")

                            for (i in 1..resolve.instructions.size()) {
                                val insn: MethodInsnNode = resolve.instructions.get(i) as MethodInsnNode
                                if (insn.type == AbstractInsnNode.METHOD_INSN) {
                                    if (getContents == insn.owner + "." + insn.name + insn.desc) {
                                        if (
                                            resolve.instructions.get(i - 5).opcode == Opcodes.NEW &&
                                            resolve.instructions.get(i - 4).opcode == Opcodes.DUP &&
                                            resolve.instructions.get(i - 3).opcode == Opcodes.ALOAD &&
                                            resolve.instructions.get(i - 2).opcode == Opcodes.INVOKESPECIAL &&
                                            resolve.instructions.get(i - 1).opcode == Opcodes.ALOAD
                                        ) {
                                            resolve.instructions.set(resolve.instructions.get(i - 5), InsnNode(Opcodes.NOP)) // NEW File
                                            resolve.instructions.set(resolve.instructions.get(i - 4), InsnNode(Opcodes.NOP)) // DUP
                                            resolve.instructions.set(resolve.instructions.get(i - 2), InsnNode(Opcodes.NOP)) // INVOKESTATIC <init>
                                            insn.owner = rangeExtractor
                                            insn.desc = hookDescResolve
                                            logger.lifecycle("Patched " + node.name)
                                        } else {
                                            throw IllegalStateException("Found Util.getFileCharContents call, with unexpected context")
                                        }
                                    }
                                }
                            }
                        } else if (rangeExtractor == target) {
                            logger.lifecycle("Tansforming: $target From: $lib")
                            val marker = node.methods.find{ "hasBeenASMPatched()Z" == it.name + it.desc } ?: throw RuntimeException("Failed to patch $target: Could not find method hasBeenASMPatched()Z")
                            marker.instructions.clear()
                            marker.instructions.add(InsnNode(Opcodes.ICONST_1))
                            marker.instructions.add(InsnNode(Opcodes.IRETURN))
                            logger.lifecycle("Patched: " + node.name)
                        }

                        val writer = ClassWriter(0)
                        node.accept(writer)

                        remove.add(target)
                        val nentry = ZipEntry(entry.name)
                        nentry.time = 0
                        zout.putNextEntry(nentry)
                        zout.write(writer.toByteArray())
                        zout.closeEntry()
                    }
                    toProcess.removeAll(remove)
                }
            }
        }
    }
}
