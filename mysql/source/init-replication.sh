#!/bin/bash
set -e

# Replication 유저, monitor 유저 생성
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY '${MYSQL_REPL_PASSWORD:-replpassword}';
    GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';

    CREATE USER IF NOT EXISTS '${MYSQL_USER:-appuser}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD:-apppassword}';
    GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE:-casestudy}\`.* TO '${MYSQL_USER:-appuser}'@'%';

    CREATE USER IF NOT EXISTS 'monitor'@'%' IDENTIFIED BY 'monitorpassword';
    GRANT REPLICATION CLIENT, PROCESS ON *.* TO 'monitor'@'%';
    GRANT SELECT ON performance_schema.* TO 'monitor'@'%';

    FLUSH PRIVILEGES;
EOSQL
