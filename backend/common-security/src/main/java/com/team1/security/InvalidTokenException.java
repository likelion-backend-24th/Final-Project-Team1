package com.team1.security;

public class InvalidTokenException extends RuntimeException{
    public InvalidTokenException(String message, Throwable cause){
        super(message, cause);
    }
}
