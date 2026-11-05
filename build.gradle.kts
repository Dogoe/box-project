plugins {
    java
    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.endel"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    implementation("org.postgresql:postgresql")
    implementation ("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    //Utility
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
    runtimeOnly ("io.jsonwebtoken:jjwt-jackson:0.12.6")  // for JSON processing with Jackson
    // Pure-Java HTML -> PDF rendering for downloadable quotes/kit spec sheets.
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.108")
    // Product/kit photos go to Cloudflare R2 through its S3-compatible API. The JDK's own HTTP
    // client is enough for occasional admin uploads, so the Netty/Apache clients are left out.
    implementation(platform("software.amazon.awssdk:bom:2.31.50"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")
    // Lets ImageIO read WebP uploads (common for photos saved from the web).
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    // Auto-restarts the app on recompile when run locally (IDE or ./gradlew bootRun).
    // developmentOnly is excluded from bootJar by Spring Boot's Gradle plugin, so it
    // never ships in the Docker image built from that jar.
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    //testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
