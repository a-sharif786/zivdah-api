pipeline {
    agent any

    tools {
        jdk 'JDK-17'
        maven 'Maven-3.9.11'
    }

    environment {
        MAVEN_OPTS = '-Xmx1024m'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build All Microservices') {
            steps {
                echo 'Building all microservices using parent POM...'
                bat 'mvn clean install -DskipTests -U'
            }
        }

        stage('Run Unit Tests') {
            steps {
                echo 'Running all unit tests...'
                bat 'mvn test'
            }
        }

        stage('Verify Artifacts') {
            steps {
                echo 'Listing generated JAR files...'
                bat 'dir /s /b target'
            }
        }


        stage('Docker Build') {
            steps {
                 echo 'Building Docker images for all microservices...'
                 bat 'docker compose build'
            }
        }

        stage('Docker Refresh') {
            steps {
                  echo 'Refreshing Docker environment...'
                  bat 'docker compose down -v'
                  bat 'docker compose pull'
                  bat 'docker compose up -d'
           }
        }

        /*
        stage('Deploy') {
            steps {
                echo 'Deploying microservices...'
            }
        }
        */
    }

    post {
        success {
            echo '✅ All microservices built successfully using parent POM!'
        }
        failure {
            echo '❌ Build failed. Check Maven logs.'
        }
        always {
            echo 'Pipeline finished.'
        }
    }
}
