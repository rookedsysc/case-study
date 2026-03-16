#!/bin/bash
set -e

# Source가 준비될 때까지 대기
until mysql -h mysql-source -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SELECT 1" &>/dev/null; do
    echo "Waiting for mysql-source..."
    sleep 2
done

# GTID 기반 Replication 설정 및 시작
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CHANGE REPLICATION SOURCE TO
        SOURCE_HOST='mysql-source',
        SOURCE_PORT=3306,
        SOURCE_USER='repl',
        SOURCE_PASSWORD='${MYSQL_REPL_PASSWORD:-replpassword}',
        SOURCE_AUTO_POSITION=1,
        GET_SOURCE_PUBLIC_KEY=1;

    START REPLICA;

    SET GLOBAL read_only = ON;
    SET GLOBAL super_read_only = ON;
EOSQL

echo "Replication started successfully (read_only enabled)"
