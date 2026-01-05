pipeline {
    agent any
    stages {
        stage('Build API Gateway') {
            steps {
                dir('zivdah-api-gateway') {
                    echo 'Building API Gateway...'
                    sh 'mvn clean install'
                }
            }
        }
        stage('Build Eureka Server') {
            steps {
                dir('zivdah-eureka-server') {
                    echo 'Building Eureka Server...'
                    sh 'mvn clean install'
                }
            }
        }
        stage('Build Auth Service') {
            steps {
                dir('zivdh-auth-service') {
                    echo 'Building Auth Service...'
                    sh 'mvn clean install'
                }
            }
        }
        stage('Test API Gateway') {
            steps {
                dir('api-gatewaye-service') {
                    echo 'Running tests for API Gateway...'
                    sh 'mvn test'
                }
            }
        }
        stage('Test Eureka Server') {
            steps {
                dir('zivdah-eureka-server') {
                    echo 'Running tests for Eureka Server...'
                    sh 'mvn test'
                }
            }
        }
        stage('Test Auth Service') {
            steps {
                dir('zivdh-auth-service') {
                    echo 'Running tests for Auth Service...'
                    sh 'mvn test'
                }
            }
        }
    }
    post {
        success {
            echo 'All microservices built and tested successfully!'
        }
        failure {
            echo 'Build failed for one or more microservices.'
        }
    }
}
