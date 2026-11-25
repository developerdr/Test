pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
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
    }
}
