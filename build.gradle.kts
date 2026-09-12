import kotlin.collections.filter
import org.gradle.declarative.dsl.schema.FqName.Empty.packageName
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ByteArrayResource

buildscript {
  dependencies {
    classpath("org.flywaydb:flyway-database-postgresql:13.0.0")
    classpath("org.springframework.boot:spring-boot:4.1.0")
    classpath("org.yaml:snakeyaml:2.6")
  }
}

plugins {
  kotlin("jvm") version "2.3.21"
  kotlin("plugin.spring") version "2.3.21"
  id("org.springframework.boot") version "4.1.0"
  id("io.spring.dependency-management") version "1.1.7"

  id("org.flywaydb.flyway") version "13.0.0"
  id("org.jooq.jooq-codegen-gradle") version "3.21.5"
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

ktfmt { kotlinLangStyle() }

dependencyManagement {
  imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3") }
}

group = "com.side"

version = providers.gradleProperty("appVersion").orElse("0.0.1-SNAPSHOT").get()

description = "diary"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-websocket")
  implementation("org.springframework.boot:spring-boot-starter-jooq")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("tools.jackson.module:jackson-module-kotlin")

  implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")

  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
  implementation("org.springframework.boot:spring-boot-starter-validation")

  runtimeOnly("org.postgresql:postgresql")
  jooqCodegen("org.postgresql:postgresql")

  testImplementation("org.mockito.kotlin:mockito-kotlin:6.1.0")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
        "-Xjsr305=strict",
        "-Xannotation-default-target=param-property",
    )
  }
}

sourceSets { main { java { srcDir("src/jooq/java") } } }

tasks.withType<Test> { useJUnitPlatform() }

val databaseEnvironment = StandardEnvironment()

providers.gradleProperty("spring.profiles.active").orNull?.let {
  databaseEnvironment.propertySources.addFirst(
      MapPropertySource("gradleProfiles", mapOf("spring.profiles.active" to it))
  )
}

// ponytail: 파일 기반 프로필을 공유한다. Vault 등 config.import가 필요하면 ConfigData 로더로 전환한다.
fun databaseYamlSources(name: String): List<PropertySource<*>> =
    listOf("yml", "yaml").flatMap { extension ->
      val yaml = layout.projectDirectory.file("src/main/resources/$name.$extension")
      providers
          .fileContents(yaml)
          .asText
          .orNull
          ?.let { content ->
            YamlPropertySourceLoader()
                .load(yaml.asFile.path, ByteArrayResource(content.toByteArray(Charsets.UTF_8)))
          }
          .orEmpty()
    }

fun addDatabaseSource(source: PropertySource<*>) {
  databaseEnvironment.propertySources.addAfter(
      StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
      source,
  )
}

fun activationProfiles(source: PropertySource<*>): List<String> =
    Binder(ConfigurationPropertySources.from(source).filterNotNull())
        .bind("spring.config.activate.on-profile", Bindable.listOf(String::class.java))
        .orElse(null) ?: emptyList()

val commonDatabaseSources = databaseYamlSources("application")

commonDatabaseSources.filter { activationProfiles(it).isEmpty() }.forEach(::addDatabaseSource)

val profileBinder = Binder.get(databaseEnvironment)
val databaseProfiles =
    profileBinder.bind("spring.profiles.active", Bindable.listOf(String::class.java)).orElse(null)
        ?: profileBinder
            .bind("spring.profiles.default", Bindable.listOf(String::class.java))
            .orElse(null)
        ?: listOf("default")

databaseEnvironment.setActiveProfiles(*databaseProfiles.toTypedArray())

(commonDatabaseSources + databaseProfiles.flatMap { databaseYamlSources("application-$it") })
    .filter {
      val profiles = activationProfiles(it)
      profiles.isEmpty() || databaseEnvironment.matchesProfiles(*profiles.toTypedArray())
    }
    .forEach(::addDatabaseSource)

val dbUrl = databaseEnvironment.getProperty("spring.datasource.url")
val dbUser = databaseEnvironment.getProperty("spring.datasource.username")
val dbPassword = databaseEnvironment.getProperty("spring.datasource.password")
val dbDriver = databaseEnvironment.getProperty("spring.datasource.driver-class-name")

flyway {
  url = dbUrl
  user = dbUser
  password = dbPassword
  driver = dbDriver
  schemas = arrayOf("public")
}

jooq {
  configuration {
    jdbc {
      driver = dbDriver
      url = dbUrl
      user = dbUser
      password = dbPassword
    }

    generator {
      name = "org.jooq.codegen.JavaGenerator"

      database {
        name = "org.jooq.meta.postgres.PostgresDatabase"
        inputSchema = "public"
        excludes = "flyway_schema_history"

        forcedTypes {
          forcedType {
            userType = "java.time.Instant"
            autoConverter = true
            includeExpression = ".*\\.CREATED_AT"
          }
        }
      }

      generate {
        isPojos = false
        isDaos = false
        isWhereMethodOverrides = false
      }

      target {
        packageName = "com.example.jooq.generated"
        directory = layout.projectDirectory.dir("src/jooq/java").asFile.absolutePath
      }
    }
  }
}

val migrationFiles = fileTree("src/main/resources/db/migration")

tasks.named("flywayMigrate") { inputs.files(migrationFiles) }

tasks.named("jooqCodegen") {
  dependsOn(tasks.named("flywayMigrate"))
  inputs.files(migrationFiles)
}

// tasks.named<BootBuildImage>("bootBuildImage") {
//    val imageRepository = "ghcr.io/sakur35a/practice-kotlin"
//
//    imageName.set("$imageRepository:${project.version}")
//    tags.set(listOf("$imageRepository:latest"))
//    createdDate.set("now")
//    imagePlatform.set("linux/amd64")
//
//    docker {
//        publishRegistry {
//            username.set(providers.environmentVariable("GHCR_USERNAME"))
//            password.set(providers.environmentVariable("GHCR_TOKEN"))
//        }
//    }
// }
