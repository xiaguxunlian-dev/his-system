package com.his.auth;

/**
 * 用户会话（线程安全单例）
 * 存储当前登录用户信息，全程序共享
 */
public class UserSession {

    private static volatile UserSession instance;

    private int    userId;
    private String username;
    private String displayName;
    private UserRole role;
    private Integer departmentId;
    private String  departmentName;
    private long    loginTime;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            synchronized (UserSession.class) {
                if (instance == null) {
                    instance = new UserSession();
                }
            }
        }
        return instance;
    }

    /** 登录成功后调用，设置当前用户信息 */
    public synchronized void login(int userId, String username, String displayName,
                                   UserRole role, Integer departmentId, String departmentName) {
        this.userId         = userId;
        this.username       = username;
        this.displayName    = displayName;
        this.role           = role;
        this.departmentId   = departmentId;
        this.departmentName = departmentName;
        this.loginTime      = System.currentTimeMillis();
    }

    /** 退出登录，清除会话 */
    public synchronized void logout() {
        this.userId         = 0;
        this.username       = null;
        this.displayName    = null;
        this.role           = null;
        this.departmentId   = null;
        this.departmentName = null;
        this.loginTime      = 0;
    }

    /** 是否已登录 */
    public boolean isLoggedIn() {
        return username != null && !username.isEmpty();
    }

    /** 是否是管理员 */
    public boolean isAdmin() {
        return role == UserRole.管理员;
    }

    /** 是否有权访问指定角色的模块 */
    public boolean hasRole(UserRole... allowedRoles) {
        if (role == UserRole.管理员) return true;  // 管理员可访问所有
        for (UserRole r : allowedRoles) {
            if (r == role) return true;
        }
        return false;
    }

    // Getters
    public int      getUserId()         { return userId; }
    public String   getUsername()       { return username; }
    public String   getDisplayName()    { return displayName; }
    public UserRole getRole()           { return role; }
    public Integer  getDepartmentId()   { return departmentId; }
    public String   getDepartmentName() { return departmentName; }
    public long     getLoginTime()      { return loginTime; }

    /** 格式化显示：用于窗口标题 */
    public String getDisplayInfo() {
        if (!isLoggedIn()) return "未登录";
        return displayName + "（" + (role != null ? role.getDisplayName() : "") + "）";
    }
}
