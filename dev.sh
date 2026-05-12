#!/usr/bin/env bash
# Starts backend (Spring Boot :8080) and frontend (Vite :5173) in parallel.
# Press Ctrl+C to stop both.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cleanup() {
  echo ""
  echo "Shutting down..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Starting backend  (Spring Boot :8080)..."
(cd "$SCRIPT_DIR/backend-springboot" && mvn spring-boot:run -q) &
BACKEND_PID=$!

echo "Starting frontend (Vite :5173)..."
(cd "$SCRIPT_DIR/frontend-vue" && npm run dev) &
FRONTEND_PID=$!

echo ""
echo "Backend  PID: $BACKEND_PID"
echo "Frontend PID: $FRONTEND_PID"
echo "Open http://localhost:5173 once both services are ready."
echo "Press Ctrl+C to stop."

wait
