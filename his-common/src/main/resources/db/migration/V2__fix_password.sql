-- ============================================================
-- 迁移 V2：修复默认用户密码哈希
-- V1 中密码哈希错误（BCrypt 哈希不匹配任何已知密码），
-- 导致所有默认用户无法登录。
-- 本迁移将6个默认用户的密码哈希更新为正确值（密码: admin123）
-- ============================================================

UPDATE system_users
SET password_hash = '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le'
WHERE username IN ('admin', 'guahao', 'doctor', 'nurse', 'pharmacy', 'cashier');
