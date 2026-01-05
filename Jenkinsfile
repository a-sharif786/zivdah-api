pipeline {
    agent any
    stages {
        stage('Build API Gateway') {
            steps {
                dir('zivdah-api-gateway') {
                    echo 'Building API Gateway...'
                    bat 'mvn clean install'
                }
            }
        }
        stage('Build Eureka Server') {
            steps {
                dir('zivdah-eureka-server') {
                    echo 'Building Eureka Server...'
                    bat 'mvn clean install'
                }
            }
        }
        stage('Build Auth Service') {
            steps {
                dir('zivdh-auth-service') {
                    echo 'Building Auth Service...'
                    bat 'mvn clean install'
                }
            }
        }
        stage('Test API Gateway') {
            steps {
                dir('zivdah-api-gateway') {
                    echo 'Testing API Gateway...'
                    bat 'mvn test'
                }
            }
        }
        stage('Test Eureka Server') {
            steps {
                dir('zivdah-eureka-server') {
                    echo 'Testing Eureka Server...'
                    bat 'mvn test'
                }
            }
        }
        stage('Test Auth Service') {
            steps {
                dir('zivdh-auth-service') {
                    echo 'Testing Auth Service...'
                    bat 'mvn test'
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
