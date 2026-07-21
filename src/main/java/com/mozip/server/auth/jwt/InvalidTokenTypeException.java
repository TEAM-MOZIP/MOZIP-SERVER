package com.mozip.server.auth.jwt;

import io.jsonwebtoken.JwtException;

public class InvalidTokenTypeException extends JwtException {

    public InvalidTokenTypeException(String message) {
        super(message);
    }
}
