#!/usr/bin/env sh
cd "$(dirname "$0")/.."
./mvnw -DskipTests package
cd frontend
npm run build
