-- DevNest MySQL 8 数据库初始化
-- 请先使用具备建库权限的管理员账号执行本文件。

CREATE DATABASE IF NOT EXISTS devnest
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 推荐为应用创建最小权限账号。请替换用户名、主机范围和强密码后再取消注释。
-- CREATE USER IF NOT EXISTS 'devnest'@'localhost' IDENTIFIED BY '请替换为强密码';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
--     ON devnest.* TO 'devnest'@'localhost';
-- FLUSH PRIVILEGES;

-- 验证数据库：
SHOW CREATE DATABASE devnest;
