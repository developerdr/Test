package com.example.demo.perf;

import com.intuit.karate.gatling.javaapi.KarateProtocolBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static com.intuit.karate.gatling.javaapi.KarateDsl.*;
import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Load test that replays the functional Karate feature with many virtual users.
 * Target app: -DbaseUrl=http://localhost:9091 (read by karate-config.js).
 * Load shape: -Dusers=20 -DrampSeconds=10 (defaults below).
 */
public class BooksSimulation extends Simulation {

    // Group requests by URL pattern in the Gatling report, so /api/books/1, /2, ... show as one line
    KarateProtocolBuilder protocol = karateProtocol(
            uri("/api/hello").nil(),
            uri("/api/books").nil(),
            uri("/api/books/{id}").nil());

    ScenarioBuilder books = scenario("Books API")
            .exec(karateFeature("classpath:features/books.feature"));

    {
        int users = Integer.getInteger("users", 20);
        int rampSeconds = Integer.getInteger("rampSeconds", 10);

        setUp(books.injectOpen(rampUsers(users).during(rampSeconds)))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(95.0).lt(1000));
    }
}
