// Notifications are written to the build console log
def notify(String status) {
    echo """
=========== NOTIFICATION ===========
Status:   ${status}
Job:      ${env.JOB_NAME} #${env.BUILD_NUMBER}
Branch:   ${env.BUILD_BRANCH}
Duration: ${currentBuild.durationString}
Details:  ${env.BUILD_URL}console
====================================
"""
}

def isMain() {
    return env.BUILD_BRANCH == 'main'
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
        cron('H 2 * * *')
    }

    environment {
        // Secret comes from Jenkins credentials, never hardcoded
        JWT_SECRET = credentials('jwt-secret')
        // Fixed project name => predictable image names (buy02-<service>)
        COMPOSE_PROJECT_NAME = 'buy02'
        SONAR_HOST_URL = 'http://sonarqube:9000'
        SONAR_TOKEN = credentials('sonar-token')
    }

    stages {

        stage('Checkout') {
            steps {
                script {
                    def scmVars = checkout scm
                    if (!scmVars.GIT_BRANCH) {
                        error 'Could not tell which branch is being built'
                    }
                    env.BUILD_BRANCH = scmVars.GIT_BRANCH.replaceFirst('^origin/', '')
                }
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

        stage('SonarQube Analysis') {
            when { expression { isMain() } }
            steps {
                echo 'Analysing code quality and waiting for the quality gate...'
                sh 'sonar-scanner -Dsonar.qualitygate.wait=true'
            }
        }

        stage('Build Images') {
            when { expression { isMain() } }
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
            when { expression { isMain() } }
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
            script {
                if (isMain()) {
                    sh 'docker compose down --remove-orphans || true'
                }
            }
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
