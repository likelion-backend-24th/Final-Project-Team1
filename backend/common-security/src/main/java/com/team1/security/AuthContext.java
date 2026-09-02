package com.team1.security;

public class AuthContext {
    private static final ThreadLocal<AuthenticatedUser> holder = new ThreadLocal<>();

    public static void set(AuthenticatedUser user){
        holder.set(user);
    }

    public static AuthenticatedUser get(){
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}
