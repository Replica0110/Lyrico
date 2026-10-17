pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // SaltUI's HiddenApiBypass dependency can receive HTTP 403 from Maven Central.
        // Route only this group through the mirror, including its POM and AAR.
        exclusiveContent {
            forRepository {
                maven {
                    name = "HiddenApiBypassMirror"
                    url = uri("https://maven.aliyun.com/repository/central")
                }
            }
            filter {
                includeGroup("org.lsposed.hiddenapibypass")
            }
        }
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

rootProject.name = "Lyrico"
include(":lyrico-app")
include(":lyrico-audiotag")
