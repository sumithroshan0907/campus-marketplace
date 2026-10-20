package com.campus.marketplace.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserUpdateRequest {
    private String fullName;
    private String department;
    private String year;
    private String studentId;
    private String currentPassword;
    private String newPassword;
}
