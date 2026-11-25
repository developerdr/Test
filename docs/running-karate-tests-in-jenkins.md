# Running the Karate tests through Jenkins

## How it works

Push a commit to `master` on [developerdr/Test](https://github.com/developerdr/Test) and Jenkins runs the Karate tests within about 2 minutes. No button to press.

- **The app:** a small Spring Boot REST API. `GET /api/hello` returns a greeting; `/api/books` adds, lists and deletes books held in memory.
- **The tests:** Karate scenarios in `src/test/java/features/books.feature`. JUnit starts the app on a random port and runs every `.feature` file against it.
- **Jenkins:** runs in Docker on your machine. It checks GitHub every 2 minutes (SCM polling) and builds any new commit using the `Jenkinsfile` in the repo.

Jenkins polls rather than waiting for a GitHub webhook because it runs on `localhost`, which GitHub cannot reach.

## Start Jenkins

You need Docker Desktop running and a copy of the repo. Java and Maven are only needed to run tests on your own machine.

1. Clone the repo: `git clone https://github.com/developerdr/Test.git && cd Test`
2. Start Jenkins: `docker compose up -d --build`. The first start takes a few minutes while it installs plugins.
3. Open http://localhost:8090 and log in as `admin` / `admin`.
4. Open the `springboot-karate-demo` job. It is created automatically.

To stop Jenkins, run `docker compose down`. Builds and settings are kept in a Docker volume and come back on the next `docker compose up -d`.

| What | Address |
| --- | --- |
| Jenkins | http://localhost:8090 |
| Job | http://localhost:8090/job/springboot-karate-demo/ |
| App deployed by the pipeline | http://localhost:9091/api/hello |

## Trigger a test run

**Automatically:** commit and push to `master`.

```
git add -A
git commit -m "Add a Karate scenario"
git push origin master
```

Within about 2 minutes a new build appears under **Build History** on the job page. Its **Polling Log** link shows when Jenkins saw the commit.

**Manually:** on the job page, click **Build Now**. Use this to re-run tests without a new commit.

Jenkins polls using the settings of the last build. After the job's repository or branch changes, click **Build Now** once; pushes trigger builds on their own after that.

## What the pipeline does and where to see results

A failing Karate scenario, security finding or load-test threshold fails the build. Nothing is deployed if tests or scans fail.

1. **Checkout:** pulls the commit from GitHub.
2. **Build & Karate Tests:** runs `./mvnw -B clean verify`, which compiles the app and runs every Karate scenario.
3. **Security Scans** (these two run side by side):
    - **SAST:** SpotBugs with the Find Security Bugs plugin scans the compiled code. Any finding of Medium severity or higher fails the build.
    - **Black Duck SCA:** the Black Duck Security Scan plugin checks the open-source dependencies. Blocker or Critical issues fail the build. Skipped when no Black Duck server is configured (see below).
4. **Archive:** saves `target/demo-0.0.1-SNAPSHOT.jar` on the build page.
5. **Deploy:** stops the previous copy of the app and starts the new jar on port 9091.
6. **Smoke Test:** calls http://localhost:9091/api/hello until it answers, or fails after 60 seconds.
7. **Load Test (Karate + Gatling):** replays `books.feature` with many virtual users against the deployed app. Fails if more than 1% of requests fail or the 95th-percentile response time reaches 1 second.

Open a build from **Build History** to see its results:

| Link on the build page | What it shows | Plugin |
| --- | --- | --- |
| Cucumber reports | Pass/fail per feature, scenario and step, with charts | Cucumber Reports |
| Karate Report | Karate's own report, with each HTTP request and response | HTML Publisher |
| Test Result | JUnit-style pass/fail counts and trend across builds | JUnit |
| SpotBugs Warnings | SAST findings, with the line of code for each | Warnings Next Generation |
| Console Output (Black Duck SCA stage) | Scan summary and a link to the full results on your Black Duck server | Black Duck Security Scan |
| Gatling | Response times, requests per second and errors under load | Gatling |
| Console Output | The full build log; search for `scenarios:` for the Karate summary | built in |

## Load test settings

Click **Build with Parameters** on the job page to change the load:

| Parameter | Default | Meaning |
| --- | --- | --- |
| `RUN_LOAD_TEST` | on | Turn the load test stage on or off |
| `LOAD_USERS` | 20 | Number of virtual users; each runs every scenario in `books.feature` once |
| `LOAD_RAMP_SECONDS` | 10 | Seconds over which those users start |

The load scenario and its pass/fail thresholds are in `src/test/java/com/example/demo/perf/BooksSimulation.java`. To run it on your machine, start the app, then run:

```
./mvnw test-compile gatling:test -DbaseUrl=http://localhost:8080 -Dusers=20 -DrampSeconds=10
```

The report is written to `target/gatling/`.

## Turn on the Black Duck scan

The Black Duck stage needs a Black Duck SCA server and an API token. Without them it is skipped and the rest of the pipeline still runs.

1. Create a file named `.env` next to `docker-compose.yml` (it is git-ignored, so the token is never committed):

    ```
    BLACKDUCK_URL=https://your-blackduck-server.example.com
    BLACKDUCK_API_TOKEN=your-api-token
    ```

2. Recreate Jenkins so it reads them: `docker compose up -d --build`.
3. Push a commit or click **Build Now**. The **Black Duck SCA** stage now runs.

The token is stored in Jenkins as the secret credential `blackduck-token`. To run SAST on your machine: `./mvnw compile spotbugs:check`.

## Add or change a Karate test

Put new scenarios in `src/test/java/features/books.feature`, or in a new `.feature` file in the same folder. Every `.feature` file there runs automatically. `baseUrl` already points at the running app.

```
Scenario: List books
  Given url baseUrl
  And path 'api', 'books'
  When method get
  Then status 200
  And match response == '#array'
```

Check it on your own machine before pushing (needs Java 21 or newer):

```
./mvnw verify
```

The console prints `scenarios: N | passed: N | failed: 0` when all pass. Open `target/karate-reports/karate-summary.html` in a browser for the full report. Then commit and push to `master` to run it in Jenkins.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| No build after a push | Wait 2 minutes, then check **Git Polling Log** on the job page. Make sure you pushed to `master`, not another branch. |
| `port is already allocated` on `docker compose up` | Another program uses 8090 or 9091. Change the left-hand port in `docker-compose.yml`, and `APP_PORT` in the `Jenkinsfile` for 9091. |
| Build fails at Build & Karate Tests | Open **Cucumber reports** or **Karate Report** to see the failing step, fix it, and run `./mvnw verify` locally before pushing again. |
| Karate Report shows no styling | Jenkins blocks its scripts by default. `docker-compose.yml` turns that check off for this local demo; keep it on for a shared Jenkins. |
| Smoke Test fails | The app did not start. The end of the build log prints the last 50 lines of the app's log. |
| SAST stage fails | Open **SpotBugs Warnings** to see each finding. Fix the code, or exclude a false positive with a SpotBugs exclude filter. |
| Black Duck stage fails before scanning | Check `BLACKDUCK_URL` and the token. Jenkins also needs internet access to download the Black Duck Bridge CLI. |
| Load test fails | Open **Gatling** to see which request was slow or failed. Lower `LOAD_USERS` to check whether it only fails under heavy load. |
| Repo becomes private | Jenkins can no longer clone it. Add a GitHub token as a Jenkins credential and select it in the job's Git settings. |
