plugins {
    id("fabric-loom") version Versions.Fabric.loom
    id("io.github.juuxel.loom-quiltflower") version Versions.Fabric.loomQuiltflower
}


configurations {
    val common by creating
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
}

dependencies {
    shadedApi(project(":common:implementation:base"))
    
    "compileOnly"("net.fabricmc:sponge-mixin:${Versions.Fabric.mixin}")
    "annotationProcessor"("net.fabricmc:sponge-mixin:${Versions.Fabric.mixin}")
    "annotationProcessor"("net.fabricmc:fabric-loom:${Versions.Fabric.loom}")
    
    "common"(project(path = ":platforms:mixin-common", configuration = "namedElements")) { isTransitive = false }
    shaded(project(path = ":platforms:mixin-common", configuration = "transformProductionFabric")) { isTransitive = false }
    
    minecraft("com.mojang:minecraft:${Versions.Fabric.minecraft}")
    mappings("net.fabricmc:yarn:${Versions.Fabric.yarn}:v2")
    
    modImplementation("net.fabricmc:fabric-loader:${Versions.Fabric.fabricLoader}")
    
    setOf("fabric-lifecycle-events-v1", "fabric-resource-loader-v0", "fabric-api-base", "fabric-command-api-v1").forEach { apiModule ->
        val module = fabricApi.module(apiModule, Versions.Fabric.fabricAPI)
        modImplementation(module)
        include(module)
    }
    
    modImplementation("cloud.commandframework", "cloud-fabric", Versions.Libraries.cloud)
    include("cloud.commandframework", "cloud-fabric", Versions.Libraries.cloud)
}

loom {
    accessWidenerPath.set(file("src/main/resources/terra.accesswidener"))
    mixin {
        defaultRefmapName.set("terra-fabric-refmap.json")
    }
}


addonDir(project.file("./run/config/Terra/addons"), tasks.named("runClient").get())
addonDir(project.file("./run/config/Terra/addons"), tasks.named("runServer").get())


tasks {
    compileJava {
        options.release.set(17)
    }
    
    remapJar {
        inputFile.set(shadowJar.get().archiveFile)
        archiveFileName.set("${rootProject.name.capitalize()}-${project.version}.jar")
    }
}
