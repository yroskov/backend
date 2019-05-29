
description = "CoL backend services (DAO)"

dependencies {
    api(platform(rootProject))
    implementation(platform("io.dropwizard:dropwizard-bom"))
    compile(project(":colplus-api"))
    compile(project(":colplus-parser"))
    compile("org.postgresql:postgresql")
    compile("com.zaxxer:HikariCP")
    compile("org.mybatis:mybatis")
    compile("com.univocity:univocity-parsers")
    compile("org.imgscalr:imgscalr-lib")
    compile("com.google.guava:guava")
    compile("com.google.code.findbugs:jsr305")
    compile("com.fasterxml.jackson.core:jackson-annotations")
    compile("com.fasterxml.jackson.core:jackson-core")
    compile("com.fasterxml.jackson.core:jackson-databind")
    compile("org.apache.commons:commons-lang3")
    compile("org.slf4j:slf4j-api")
    compile("com.twelvemonkeys.imageio:imageio-jpeg")
    compile("com.twelvemonkeys.imageio:imageio-tiff")
    compile("com.twelvemonkeys.imageio:imageio-bmp")
    compile("com.twelvemonkeys.imageio:imageio-psd")
    compile("com.twelvemonkeys.imageio:imageio-pict")
    compile("com.twelvemonkeys.imageio:imageio-icns")
    compile("javax.validation:validation-api")
    compile("org.gbif:dwc-api")
    compile("io.github.java-diff-utils:java-diff-utils")
    compile("org.elasticsearch.client:elasticsearch-rest-client")
    testCompile("junit:junit")
    testCompile(project(":colplus-api", "testArchives"))
    testCompile("org.javers:javers-core")
    testCompile("ch.qos.logback:logback-classic")
    testCompile("pl.allegro.tech:embedded-elasticsearch")
}

