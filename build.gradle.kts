import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("antlr")
    id("application")
    alias(libs.plugins.shadow)
    alias(libs.plugins.lombok)
}

group = "dev.anvilcraft.base"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Main-Class" to "dev.anvilcraft.base.Main"
            )
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Main-Class" to "dev.anvilcraft.base.Main"
            )
        )
    }
}

application {
    mainClass = "dev.anvilcraft.base.Main"
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-package", "dev.anvilcraft.base.wenyan.parser")
}

tasks.compileJava {
    dependsOn(tasks.generateGrammarSource)
}

tasks.test {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}

lombok {
    version = "1.18.34"
}
