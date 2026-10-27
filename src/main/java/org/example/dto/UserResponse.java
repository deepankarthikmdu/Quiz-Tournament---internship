package org.example.dto;

/** User data returned by the API, excluding the password. */
public record UserResponse(String userId, String username, String name) { }
