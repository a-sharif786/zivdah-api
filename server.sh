#!/bin/bash

APP_DIR="/opt/new_zivdah_backend"

cd "$APP_DIR" || exit 1

case "$1" in

  start)
    echo "======================================"
    echo "Starting Zivdah Backend"
    echo "======================================"

    docker compose up -d

    echo ""
    echo "Services:"
    docker compose ps
    ;;

  stop)
    echo "======================================"
    echo "Stopping Zivdah Backend"
    echo "======================================"

    docker compose stop
    ;;

  restart)
    echo "======================================"
    echo "Restarting Zivdah Backend"
    echo "======================================"

    docker compose restart

    echo ""
    docker compose ps
    ;;

  rebuild)
    echo "======================================"
    echo "Rebuilding Zivdah Backend"
    echo "======================================"

    docker compose down
    docker compose build --no-cache
    docker compose up -d

    echo ""
    docker compose ps
    ;;

  update)
    echo "======================================"
    echo "Updating Zivdah Backend"
    echo "======================================"

    docker compose down
    docker compose build
    docker compose up -d

    echo ""
    docker compose ps
    ;;

  status)
    echo "======================================"
    echo "Zivdah Backend Status"
    echo "======================================"

    docker compose ps
    ;;

  logs)
    if [ -z "$2" ]; then
        docker compose logs --tail=100
    else
        docker compose logs --tail=100 "$2"
    fi
    ;;

  follow)
    if [ -z "$2" ]; then
        docker compose logs -f
    else
        docker compose logs -f "$2"
    fi
    ;;

  db)
    echo "======================================"
    echo "PostgreSQL Status"
    echo "======================================"

    docker compose ps postgres

    echo ""
    echo "Testing PostgreSQL..."

    docker exec zivdah-postgres pg_isready \
      -U "${DB_USERNAME:-postgres}" \
      -d "${DB_NAME:-zivdah_db}"
    ;;

  gateway)
    docker compose logs --tail=100 api-gateway
    ;;

  user)
    docker compose logs --tail=100 user-service
    ;;

  auth)
    docker compose logs --tail=100 auth-service
    ;;

  eureka)
    docker compose logs --tail=100 eureka-server
    ;;

  ps)
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    ;;

  *)
    echo ""
    echo "Zivdah Backend Server"
    echo ""
    echo "Usage:"
    echo "  ./server.sh start       Start all services"
    echo "  ./server.sh stop        Stop all services"
    echo "  ./server.sh restart     Restart all services"
    echo "  ./server.sh rebuild     Rebuild without cache"
    echo "  ./server.sh update      Build and restart"
    echo "  ./server.sh status      Show compose status"
    echo "  ./server.sh ps          Show Docker containers"
    echo "  ./server.sh logs        Show all logs"
    echo "  ./server.sh follow      Follow all logs"
    echo "  ./server.sh db          Check PostgreSQL"
    echo "  ./server.sh gateway     Gateway logs"
    echo "  ./server.sh user        User service logs"
    echo "  ./server.sh auth        Auth service logs"
    echo "  ./server.sh eureka      Eureka logs"
    echo ""
    echo "Examples:"
    echo "  ./server.sh start"
    echo "  ./server.sh restart"
    echo "  ./server.sh user"
    echo "  ./server.sh follow user-service"
    echo "  ./server.sh logs postgres"
    echo ""
    ;;

esac