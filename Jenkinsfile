pipeline {
    agent any

    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9.12'
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

        stage('SonarQube Analysis') {
             steps {
                 echo 'Running SonarQube analysis...'
                 withSonarQubeEnv('SonarQube') {
                 bat 'mvn sonar:sonar -Dsonar.projectKey=my-project -Dsonar.host.url=%SONAR_HOST_URL% -Dsonar.login=%SONAR_AUTH_TOKEN%'
                 }
             }
        }

        stage('Quality Gate') {
             steps {
                  timeout(time: 5, unit: 'MINUTES') {
                      waitForQualityGate abortPipeline: true
                  }
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
