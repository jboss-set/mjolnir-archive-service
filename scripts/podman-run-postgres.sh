#!/bin/bash

podman run -d --name mjolnir-archive-service-postgres \
  -e POSTGRES_USER=mjolnir \
  -e POSTGRES_PASSWORD=mjolnir \
  -e POSTGRES_DB=mjolnir \
  -p 5432:5432 \
  -v mjolnir-pg-dev:/var/lib/postgresql/data \
  postgres
