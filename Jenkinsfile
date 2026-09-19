// Notifications are written to the build console log
def notify(String status) {
    echo """
=========== NOTIFICATION ===========
Status:   ${status}
Job:      ${env.JOB_NAME} #${env.BUILD_NUMBER}
Duration: ${currentBuild.durationString}
Details:  ${env.BUILD_URL}console
====================================
"""
}

pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
    }

    triggers {
        // Poll the repo every ~2 minutes; a new commit on main starts a build
        pollSCM('H/2 * * * *')
    }

    environment {
        // Secret comes from Jenkins credentials, never hardcoded
        JWT_SECRET = credentials('jwt-secret')
        // Fixed project name => predictable image names (buy01-<service>)
        COMPOSE_PROJECT_NAME = 'buy01'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo 'Building all modules...'
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                echo 'Running unit tests...'
                sh 'mvn test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: '**/target/surefire-reports/*', allowEmptyArchive: true
                }
            }
        }

        stage('Build Images') {
            steps {
                echo 'Backing up current images, then building new ones...'
                // Keep the last known-good image of every service as :backup for rollback
                sh '''
                    for img in $(docker compose config --images | grep "^${COMPOSE_PROJECT_NAME}-"); do
                        base="${img%%:*}"
                        if docker image inspect "$base:latest" >/dev/null 2>&1; then
                            docker tag "$base:latest" "$base:backup"
                        fi
                    done
                '''
                script { env.IMAGES_REBUILT = 'true' }
                sh 'docker compose build'
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deploying...'
                sh 'docker compose up -d'
                // Smoke check: give services time to start, then fail if any is not running
                sh '''
                    sleep 45
                    docker compose ps --all
                    if docker compose ps --all --format '{{.Service}} {{.State}}' | grep -v ' running$'; then
                        echo "One or more services are not running"
                        exit 1
                    fi
                '''
                script { notify('DEPLOYED - all services healthy') }
            }
        }
    }

    post {
        always {
            // Pipeline is complete: leave nothing running (named volumes/data are kept)
            sh 'docker compose down --remove-orphans || true'
        }
        success {
            script { notify('SUCCESS') }
        }
        failure {
            script {
                // Rollback: restore the previous images if new ones were built
                if (env.IMAGES_REBUILT == 'true') {
                    echo 'Rolling back to previous images (:backup -> :latest)...'
                    sh '''
                        for img in $(docker images --format '{{.Repository}}:{{.Tag}}' | grep "^${COMPOSE_PROJECT_NAME}-.*:backup$"); do
                            docker tag "$img" "${img%:backup}:latest"
                        done
                    '''
                }
                notify('FAILURE (rolled back)')
            }
        }
        aborted {
            script { notify('ABORTED') }
        }
    }
}
