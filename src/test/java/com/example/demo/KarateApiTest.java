package com.example.demo;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boots the app on a random port, then runs every .feature under classpath:features.
 * Writes Cucumber JSON (for the Jenkins Cucumber Reports plugin) and JUnit XML
 * to target/karate-reports.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KarateApiTest {

    @LocalServerPort
    int port;

    @Test
    void runFeatures() {
        Results results = Runner.path("classpath:features")
                .systemProperty("baseUrl", "http://localhost:" + port)
                .outputCucumberJson(true)
                .outputJunitXml(true)
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}
