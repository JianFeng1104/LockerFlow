package com.lockerflow.dto.response;

import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;

public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String phone,
        Role role,
        UserStatus status
) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus()
        );
    }
}
