import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
}

configure<LibraryExtension> {
    namespace = "bsh"

    defaultConfig {
        minSdk = 28
        lint.targetSdk = 37
        compileSdk = 37
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
        }
    }
}

val pgccTool = configurations.maybeCreate("pgccTool")

val codeGenDir = layout.projectDirectory.dir("src/main/java/bsh")
val jjtFile = layout.projectDirectory.file("src/main/jjtree/bsh.jjt")
val jjtreeDir = layout.buildDirectory.dir("generated/source/jjtree")
val jjFile = jjtreeDir.get().file("bsh.jj")

val jjtreeGen = tasks.register<JavaExec>("jjtreeGen") {
    group = "codegen"
    description = "JJTree file build"

    classpath = pgccTool
    mainClass.set("com.helger.pgcc.jjtree.Main")
    args(
        "-OUTPUT_DIRECTORY:${jjtreeDir.get().asFile.absolutePath}",
        "-BUILD_NODE_FILES=false",
        jjtFile.asFile.absolutePath
    )

    inputs.file(jjtFile)
    outputs.dir(jjtreeDir)
}

val jjtreeSync = tasks.register<Copy>("jjtreeSync") {
    group = "codegen"
    description = "JJTree file sync"
    dependsOn(jjtreeGen)

    from(jjtreeDir) {
        include("JJTParserState.java")
        include("ParserTreeConstants.java")
    }
    into(codeGenDir)
}

val javaccGen = tasks.register<JavaExec>("javaccGen") {
    group = "codegen"
    description = "JavaCC file build"
    dependsOn(jjtreeSync)

    classpath = pgccTool
    mainClass.set("com.helger.pgcc.parser.Main")
    args(
        "-OUTPUT_DIRECTORY:${codeGenDir.asFile.absolutePath}",
        "-JDK_VERSION=1.8",
        "-USER_CHAR_STREAM=false",
        jjFile.asFile.absolutePath
    )

    inputs.file(jjFile)
    outputs.file(codeGenDir.file("AbstractCharStream.java"))
    outputs.file(codeGenDir.file("CharStream.java"))
    outputs.file(codeGenDir.file("JavaCharStream.java"))
    outputs.file(codeGenDir.file("Parser.java"))
    outputs.file(codeGenDir.file("ParserConstants.java"))
    outputs.file(codeGenDir.file("ParserTokenManager.java"))
    outputs.file(codeGenDir.file("Token.java"))
    outputs.file(codeGenDir.file("TokenMgrException.java"))
}

dependencies {
    add("pgccTool", "com.helger:parser-generator-cc:2.0.1")
    implementation("com.jakewharton.android.repackaged:dalvik-dx:16.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
    )
}

plugins.withId("com.android.library") {
    tasks.named("preBuild").configure {
        dependsOn(javaccGen)
    }
}
