package platform.eshop.plaza.userservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import platform.eshop.plaza.userservice.dto.UpdateProfileRequestDTO;
import platform.eshop.plaza.userservice.dto.UserResponseDTO;
import platform.eshop.plaza.userservice.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController( UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal String userId) {
        UserResponseDTO user = userService.getById(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @Valid @RequestBody UpdateProfileRequestDTO request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(userService.updateProfile(request, userId));
    }
}
