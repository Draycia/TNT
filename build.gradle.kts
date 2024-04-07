plugins {
    id("java")
}

group = "com.github.tricksteronline"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.github.tricksteronline.Main"
    }
}