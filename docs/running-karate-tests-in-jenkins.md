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

On a brand-new Jenkins, polling starts as soon as Jenkins is up. If no build appears after a push, click **Build Now** once.

## What the pipeline does and where to see results

The build fails at stage 2 if any Karate scenario fails, and nothing is deployed.

1. **Checkout:** pulls the commit from GitHub.
2. **Build & Karate Tests:** runs `./mvnw -B clean verify`, which compiles the app and runs every Karate scenario.
3. **Archive:** saves `target/demo-0.0.1-SNAPSHOT.jar` on the build page.
4. **Deploy:** stops the previous copy of the app and starts the new jar on port 9091.
5. **Smoke Test:** calls http://localhost:9091/api/hello until it answers, or fails after 60 seconds.

Open a build from **Build History** to see its results:

| Link on the build page | What it shows | Plugin |
| --- | --- | --- |
| Cucumber reports | Pass/fail per feature, scenario and step, with charts | Cucumber Reports |
| Karate Report | Karate's own report, with each HTTP request and response | HTML Publisher |
| Test Result | JUnit-style pass/fail counts and trend across builds | JUnit |
| Console Output | The full build log; search for `scenarios:` for the Karate summary | built in |

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
| Repo becomes private | Jenkins can no longer clone it. Add a GitHub token as a Jenkins credential and select it in the job's Git settings. |
