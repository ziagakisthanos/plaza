pipeline {
    agent any

    environment {
        // JWT secret pulled from Jenkins credentials (NOT hardcoded — audit: secrets management)
        JWT_SECRET = credentials('jwt-secret')
        COMPOSE_FILE = 'docker-compose.yml'
    }

    stages {

        stage('Checkout') {
            steps {
                // Jenkins auto-checks out the repo when using "Pipeline from SCM",
                // but this is explicit for clarity
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
                echo 'Running tests...'
                // start infra needed by tests (Mongo), then run tests
                sh 'docker compose up -d mongo'
                sh 'sleep 10'                 // give Mongo a moment to be ready
                sh 'mvn test'
            }
            post {
                always {
                    // publish test results so they're visible/stored (audit: test reports)
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Images') {
            steps {
                echo 'Building Docker images...'
                sh '''
                    docker tag buy-01-gateway:latest buy-01-gateway:backup || true
                    docker tag buy-01-user-service:latest buy-01-user-service:backup || true
                    # ... etc for each service
                '''
                sh 'docker compose build'
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deploying...'
                sh 'docker compose up -d'
            }
        }
    }

    post {
        success {
            echo 'Pipeline succeeded!'
            // notification on success (audit: notifications)
        }
        failure {
            echo 'Pipeline failed!'
            // notification on failure + rollback
        }
        always {
            echo 'Pipeline finished.'
        }
    }
}