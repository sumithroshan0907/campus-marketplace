package com.campus.marketplace.service;

import com.campus.marketplace.dto.*;
import com.campus.marketplace.entity.User;
import com.campus.marketplace.entity.enums.NotificationType;
import com.campus.marketplace.entity.enums.Role;
import com.campus.marketplace.exception.*;
import com.campus.marketplace.repository.ListingRepository;
import com.campus.marketplace.repository.UserRepository;
import com.campus.marketplace.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;

    @Value("${app.college.email-domain}")
    private String collegeEmailDomain;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate passwords match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("Email address is required");
        }

        String emailLower = request.getEmail().toLowerCase().trim();

        // Validate college email domain ONLY if mailEnabled is true and collegeEmailDomain is configured (and not 'any' or '*')
        if (mailEnabled && collegeEmailDomain != null 
                && !collegeEmailDomain.isBlank() 
                && !collegeEmailDomain.equalsIgnoreCase("any") 
                && !collegeEmailDomain.equals("*")) {
            boolean matchesCollege = emailLower.endsWith(collegeEmailDomain.toLowerCase())
                    || emailLower.endsWith(".edu")
                    || emailLower.endsWith(".ac.in");
            if (!matchesCollege) {
                throw new BadRequestException("Only college email addresses (" + collegeEmailDomain + ") are allowed");
            }
        }

        // Check duplicates
        if (userRepository.existsByEmail(emailLower)) {
            throw new DuplicateResourceException("An account with email " + request.getEmail() + " is already registered. Please sign in.");
        }

        // Handle Student ID (auto-assign if empty)
        String studentId = request.getStudentId();
        if (studentId == null || studentId.trim().isEmpty()) {
            studentId = "STU" + (System.currentTimeMillis() % 1000000);
        } else {
            studentId = studentId.trim();
        }

        if (userRepository.existsByStudentId(studentId)) {
            if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
                throw new DuplicateResourceException("Student ID " + studentId + " is already registered");
            } else {
                studentId = studentId + "_" + (System.currentTimeMillis() % 1000);
            }
        }

        // Create user with verified status so they can immediately sign in and use the platform
        String verificationToken = UUID.randomUUID().toString();
        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(emailLower)
                .password(passwordEncoder.encode(request.getPassword()))
                .studentId(studentId)
                .department(request.getDepartment() != null && !request.getDepartment().isBlank() ? request.getDepartment().trim() : "General")
                .year(request.getYear() != null && !request.getYear().isBlank() ? request.getYear().trim() : "1st Year")
                .role(Role.STUDENT)
                .emailVerified(true)
                .accountActive(true)
                .rating(5.0)
                .ratingCount(0)
                .emailVerificationToken(verificationToken)
                .emailVerificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();

        user = userRepository.save(user);

        // Send welcome notification
        try {
            notificationService.createNotification(user, NotificationType.EMAIL_VERIFICATION,
                    "Welcome to Campus Marketplace!",
                    "Your account has been created successfully. Start browsing and listing items!");
        } catch (Exception ignored) {}

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(mapToUserResponse(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isAccountActive()) {
            throw new ForbiddenException("Your account has been blocked. Please contact admin.");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(mapToUserResponse(user))
                .build();
    }

    @Transactional
    public ApiResponse verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if (user.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token has expired");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);

        return ApiResponse.success("Email verified successfully");
    }

    @Transactional
    public ApiResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with this email"));

        String resetToken = UUID.randomUUID().toString();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        // Log the reset link for dev mode
        System.out.println("========================================");
        System.out.println("Password reset link for " + user.getEmail());
        System.out.println("Token: " + resetToken);
        System.out.println("URL: http://localhost:5173/reset-password?token=" + resetToken);
        System.out.println("========================================");

        notificationService.createNotification(user, NotificationType.PASSWORD_RESET,
                "Password Reset Requested",
                "A password reset has been requested for your account.");

        return ApiResponse.success("Password reset link has been sent to your email");
    }

    @Transactional
    public ApiResponse resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);

        return ApiResponse.success("Password reset successfully");
    }

    @Transactional
    public UserResponse updateProfile(User user, UserUpdateRequest request) {
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment().trim());
        }
        if (request.getYear() != null) {
            user.setYear(request.getYear().trim());
        }
        if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
            String newSid = request.getStudentId().trim();
            if (!newSid.equalsIgnoreCase(user.getStudentId())) {
                if (userRepository.existsByStudentId(newSid)) {
                    throw new DuplicateResourceException("Student ID already in use by another account");
                }
                user.setStudentId(newSid);
            }
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BadRequestException("Current password is incorrect");
            }
            if (request.getNewPassword().length() < 6) {
                throw new BadRequestException("New password must be at least 6 characters");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }
        user = userRepository.save(user);
        return mapToUserResponse(user);
    }

    public UserResponse mapToUserResponse(User user) {
        long listingCount = listingRepository.countBySeller(user);
        long soldCount = listingRepository.countBySellerAndStatus(user,
                com.campus.marketplace.entity.enums.ListingStatus.SOLD);

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .department(user.getDepartment())
                .year(user.getYear())
                .studentId(user.getStudentId())
                .profileImage(user.getProfileImage())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .accountActive(user.isAccountActive())
                .rating(user.getRating())
                .ratingCount(user.getRatingCount())
                .listingCount(listingCount)
                .soldCount(soldCount)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
