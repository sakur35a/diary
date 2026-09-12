"""Run with python3 gradle/test_database_config.py; no database connection is made."""

import json
import os
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory


root = Path(__file__).resolve().parents[1]
with TemporaryDirectory(prefix="diary-db-profiles-") as directory:
    project = Path(directory)
    for name in ("build.gradle.kts", "settings.gradle"):
        shutil.copy(root / name, project / name)
    resources = project / "src/main/resources"
    resources.mkdir(parents=True)
    (resources / "application.yaml").write_text("""spring:
  profiles:
    default: [local]
  datasource:
    url: jdbc:postgresql://example.invalid/base
    username: shared-user
    password: base-password
    driver-class-name: org.postgresql.Driver
---
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    username: prod-user
---
spring:
  config:
    activate:
      on-profile: unused
  datasource:
    driver-class-name: wrong.Driver
""")
    (resources / "application-local.yaml").write_text("""spring:
  datasource:
    url: jdbc:postgresql://example.invalid/local
    password: 1234
""")
    (resources / "application-prod.yml").write_text("""spring:
  datasource:
    url: jdbc:postgresql://example.invalid/prod
    password: ${DB_CONFIG_TEST_PASSWORD:fallback-password}
""")
    script = project / "verify.init.gradle"
    script.write_text("""gradle.projectsEvaluated {
    def project = gradle.rootProject
    def expected = new groovy.json.JsonSlurper().parseText(System.getenv('DB_CONFIG_TEST_EXPECTED'))
    def flyway = project.extensions.getByName('flyway')
    def jdbc = project.extensions.getByName('jooq').executions.getByName('').configuration.jdbc
    boolean flywayMatches = [flyway.url, flyway.user, flyway.password, flyway.driver] == expected
    boolean jooqMatches = [jdbc.url, jdbc.user, jdbc.password, jdbc.driver] == expected
    assert flywayMatches: 'Flyway did not use the selected Spring profile'
    assert jooqMatches: 'jOOQ did not use the selected Spring profile'
}
""")
    cases = [
        ("default", [], {}, "local", "shared-user", "1234"),
        ("Gradle property", ["-Pspring.profiles.active=prod"], {}, "prod", "prod-user", "fallback-password"),
        ("system property", ["-Dspring.profiles.active=prod"], {}, "prod", "prod-user", "fallback-password"),
        ("environment", [], {"SPRING_PROFILES_ACTIVE": "prod", "DB_CONFIG_TEST_PASSWORD": "secret:# value"}, "prod", "prod-user", "secret:# value"),
        ("multiple profiles", ["-Pspring.profiles.active=prod,local"], {}, "local", "prod-user", "1234"),
        ("selector precedence", ["-Pspring.profiles.active=local", "-Dspring.profiles.active=prod"], {"SPRING_PROFILES_ACTIVE": "prod"}, "local", "shared-user", "1234"),
        ("DB environment override", [], {"SPRING_DATASOURCE_USERNAME": "env-user"}, "local", "env-user", "1234"),
    ]
    for label, arguments, overrides, database, username, password in cases:
        environment = {
            key: value for key, value in os.environ.items()
            if not key.startswith(("SPRING_", "DB_CONFIG_TEST_"))
        }
        environment.update(overrides)
        environment["DB_CONFIG_TEST_EXPECTED"] = json.dumps([
            f"jdbc:postgresql://example.invalid/{database}",
            username, password, "org.postgresql.Driver",
        ])
        result = subprocess.run(
            [str(root / "gradlew"), "-p", str(project), "-I", str(script),
             "help", "--quiet", "--console=plain", *arguments],
            env=environment, capture_output=True, text=True,
        )
        assert result.returncode == 0, f"{label}:\n{result.stdout}\n{result.stderr}"
        print(f"PASS: {label}", flush=True)
