pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        // poll GitHub every ~2 minutes; a new commit on master starts a build
        pollSCM('H/2 * * * *')
    }

    parameters {
        booleanParam(name: 'RUN_LOAD_TEST', defaultValue: true, description: 'Run the Gatling load test after deploy')
        string(name: 'LOAD_USERS', defaultValue: '20', description: 'Virtual users for the load test')
        string(name: 'LOAD_RAMP_SECONDS', defaultValue: '10', description: 'Seconds over which users are ramped up')
    }

    environment {
        APP_PORT = '9091'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Karate Tests') {
            steps {
                sh 'chmod +x mvnw && ./mvnw -B clean verify'
            }
            post {
                always {
                    // JUnit XML from Karate -> "Test Result" trend
                    junit allowEmptyResults: true, testResults: 'target/karate-reports/*.xml'
                    // Cucumber JSON from Karate -> Cucumber Reports plugin
                    cucumber fileIncludePattern: '*.json',
                             jsonReportDirectory: 'target/karate-reports',
                             buildStatus: 'FAILURE'
                    // Karate's own HTML report
                    publishHTML(target: [
                        reportName : 'Karate Report',
                        reportDir  : 'target/karate-reports',
                        reportFiles: 'karate-summary.html',
                        keepAll    : true,
                        alwaysLinkToLastBuild: true,
                        allowMissing: true
                    ])
                }
            }
        }

        stage('Security Scans') {
            parallel {
                stage('SAST (SpotBugs + Find Security Bugs)') {
                    steps {
                        // fails the build on any Medium-or-higher finding
                        sh './mvnw -B spotbugs:check'
                    }
                    post {
                        always {
                            recordIssues tool: spotBugs(pattern: 'target/spotbugsXml.xml'),
                                         enabledForFailure: true
                        }
                    }
                }

                stage('Black Duck SCA') {
                    // runs only when BLACKDUCK_URL is configured (see docker-compose.yml)
                    when { expression { return env.BLACKDUCK_URL?.trim() } }
                    steps {
                        withCredentials([string(credentialsId: 'blackduck-token', variable: 'BLACKDUCK_TOKEN')]) {
                            security_scan product: 'blackducksca',
                                          blackducksca_url: env.BLACKDUCK_URL,
                                          blackducksca_token: env.BLACKDUCK_TOKEN,
                                          blackducksca_scan_failure_severities: 'BLOCKER,CRITICAL',
                                          blackducksca_reports_sarif_create: true,
                                          mark_build_status: 'FAILURE'
                        }
                    }
                }
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    # stop the previously deployed version, if any
                    if [ -f "$JENKINS_HOME/demo-app.pid" ]; then
                        kill "$(cat "$JENKINS_HOME/demo-app.pid")" 2>/dev/null || true
                        sleep 3
                    fi
                    mkdir -p "$JENKINS_HOME/deploy"
                    cp target/demo-0.0.1-SNAPSHOT.jar "$JENKINS_HOME/deploy/demo.jar"

                    # JENKINS_NODE_COOKIE stops Jenkins from killing the app when the build ends
                    JENKINS_NODE_COOKIE=dontKillMe nohup java -jar "$JENKINS_HOME/deploy/demo.jar" \
                        --server.port=$APP_PORT > "$JENKINS_HOME/deploy/demo.log" 2>&1 &
                    echo $! > "$JENKINS_HOME/demo-app.pid"
                '''
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    for i in $(seq 1 30); do
                        if curl -sf "http://localhost:$APP_PORT/api/hello?name=Jenkins"; then
                            echo; echo "App is up on port $APP_PORT"; exit 0
                        fi
                        sleep 2
                    done
                    echo "App did not start"; tail -50 "$JENKINS_HOME/deploy/demo.log"; exit 1
                '''
            }
        }

        stage('Load Test (Karate + Gatling)') {
            when { expression { return params.RUN_LOAD_TEST } }
            steps {
                // replays the Karate features with many users; fails if >1% errors or p95 >= 1s
                sh """
                    ./mvnw -B test-compile gatling:test \
                        -DbaseUrl=http://localhost:\$APP_PORT \
                        -Dusers=${params.LOAD_USERS} \
                        -DrampSeconds=${params.LOAD_RAMP_SECONDS}
                """
            }
            post {
                always {
                    gatlingArchive()
                }
            }
        }
    }
}
