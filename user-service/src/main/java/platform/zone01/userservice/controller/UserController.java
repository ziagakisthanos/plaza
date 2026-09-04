package platform.zone01.userservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import platform.zone01.userservice.dto.UserResponseDTO;
import platform.zone01.userservice.jwt.JwtService;
import platform.zone01.userservice.service.UserService;

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

}
