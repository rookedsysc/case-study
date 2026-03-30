#!/bin/bash
set -e

# Source가 준비될 때까지 대기
until mysql -h mysql-source -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SELECT 1" &>/dev/null; do
    echo "Waiting for mysql-source..."
    sleep 2
done

# GTID 기반 Replication 설정 (skip-replica-start 제거로 실서버 시작 시 자동 연결)
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CHANGE REPLICATION SOURCE TO
        SOURCE_HOST='mysql-source',
        SOURCE_PORT=3306,
        SOURCE_USER='repl',
        SOURCE_PASSWORD='${MYSQL_PASSWORD:-replpassword}',
        SOURCE_AUTO_POSITION=1,
        GET_SOURCE_PUBLIC_KEY=1;

    SET PERSIST super_read_only = ON;
EOSQL

echo "Replication configured (will auto-start on server boot)"
