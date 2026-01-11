import com.google.protobuf.gradle.*

plugins {
    id("java")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.google.protobuf") version "0.9.4"
}

group = "org.fintech"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")
    implementation(platform("io.grpc:grpc-bom:1.64.0"))
    implementation("io.grpc:grpc-stub")
    implementation("io.grpc:grpc-protobuf")
    runtimeOnly("io.grpc:grpc-netty-shaded")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    implementation(platform("software.amazon.awssdk:bom:2.25.60"))
    implementation("software.amazon.awssdk:sqs")
    implementation("software.amazon.awssdk:sts")
    implementation("software.amazon.awssdk:auth")
    implementation("software.amazon.awssdk:regions")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")

    implementation("software.amazon.awssdk:sts")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("encodeSqsMessage") {
    group = "application"
    description = "Outputs a base64-encoded TransactionRequest for SQS testing."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.fintech.tools.SqsTransactionRequestEncoder")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}
