package com.example.whitelist.util;

import com.example.whitelist.auth.AuthContext;
import com.example.whitelist.auth.CurrentUser;

public final class AuditUtils {
    private AuditUtils() {
    }

    public static String currentUsername() {
        CurrentUser currentUser = AuthContext.get();
        return currentUser == null ? "系统" : currentUser.username();
    }
}
