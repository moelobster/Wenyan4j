plugins {
    id("java")
    alias(libs.plugins.lombok)
}

group = "dev.anvilcraft.base"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

}

lombok {
    version = "1.18.34"
}
