package com.his.shared.database;

import com.his.shared.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础数据访问层
 * 提供通用的 CRUD 和查询辅助方法
 * 所有模块的 Repository 应继承此类
 */
public abstract class BaseRepository {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 获取数据库连接
     */
    protected Connection getConnection() {
        try {
            return ConnectionPool.getInstance().getConnection();
        } catch (SQLException e) {
            throw new DatabaseException("获取数据库连接失败", e);
        }
    }

    /**
     * 执行查询，返回结果列表（支持分页）
     * @param sql    原SQL（不含 LIMIT/OFFSET）
     * @param page    页码（从1开始）
     * @param pageSize 每页条数
     */
    protected <T> PageResult<T> queryPage(String sql, int page, int pageSize, RowMapper<T> mapper, Object... params) {
        // 1. 查询总数
        String countSql = "SELECT COUNT(*) FROM (" + removeOrderBy(sql) + ") AS cnt";
        int total;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            setParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                total = rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            log.error("分页计数失败: {}", countSql, e);
            throw new DatabaseException("分页计数失败", e);
        }

        // 2. 查询当前页
        String pageSql = sql + " LIMIT ? OFFSET ?";
        List<T> data = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(pageSql)) {
            setParameters(ps, params);
            int idx = params.length + 1;
            ps.setInt(idx++, pageSize);
            ps.setInt(idx, (page - 1) * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.add(mapper.mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("分页查询失败: {}", pageSql, e);
            throw new DatabaseException("分页查询失败", e);
        }

        return new PageResult<>(data, total, page, pageSize);
    }

    private String removeOrderBy(String sql) {
        // 简单移除尾 ORDER BY ... 
        int idx = sql.toUpperCase().lastIndexOf("ORDER BY");
        if (idx >= 0) {
            // 不剥除非尾部的 ORDER BY
            String tail = sql.substring(idx);
            if (!tail.contains(")")) return sql.substring(0, idx);
        }
        return sql;
    }

    /**
     * 分页查询结果
     */
    protected static class PageResult<T> {
        public final List<T> data;
        public final int total;
        public final int page;
        public final int pageSize;
        public PageResult(List<T> data, int total, int page, int pageSize) {
            this.data = data; this.total = total; this.page = page; this.pageSize = pageSize;
        }
        public int getTotalPages() { return (total + pageSize - 1) / pageSize; }
    }

    /**
     * 执行查询，返回结果列表（无分页，禁止无 LIMIT 的 SELECT *）
     */
    protected <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("查询失败: {}", sql, e);
            throw new DatabaseException("数据查询失败", e);
        }
        return results;
    }

    /**
     * 执行查询，返回单个结果
     */
    protected <T> T querySingle(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.mapRow(rs);
                }
            }
        } catch (SQLException e) {
            log.error("查询失败: {}", sql, e);
            throw new DatabaseException("数据查询失败", e);
        }
        return null;
    }

    /**
     * 执行更新(INSERT/UPDATE/DELETE)，返回影响行数
     */
    protected int executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, params);
            return ps.executeUpdate();

        } catch (SQLException e) {
            log.error("更新失败: {}", sql, e);
            throw new DatabaseException("数据更新失败", e);
        }
    }

    /**
     * 执行 INSERT 并返回自增 ID
     */
    protected int executeInsert(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setParameters(ps, params);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("插入失败: {}", sql, e);
            throw new DatabaseException("数据插入失败", e);
        }
        throw new DatabaseException("插入后未能获取自增ID");
    }

    /**
     * 执行事务
     */
    protected void executeInTransaction(TransactionCallback callback) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            callback.execute(conn);
            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("事务回滚失败", ex);
                }
            }
            throw new DatabaseException("事务执行失败", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    log.error("关闭连接失败", e);
                }
            }
        }
    }

    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof java.time.LocalDate) {
                ps.setDate(i + 1, Date.valueOf((java.time.LocalDate) param));
            } else if (param instanceof java.time.LocalDateTime) {
                ps.setTimestamp(i + 1, Timestamp.valueOf((java.time.LocalDateTime) param));
            } else {
                ps.setObject(i + 1, param);
            }
        }
    }

    /**
     * 行映射器接口
     */
    @FunctionalInterface
    protected interface RowMapper<T> {
        T mapRow(ResultSet rs) throws SQLException;
    }

    /**
     * 事务回调接口
     */
    @FunctionalInterface
    protected interface TransactionCallback {
        void execute(Connection conn) throws Exception;
    }
}
