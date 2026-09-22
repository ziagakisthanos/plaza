package platform.eshop.plaza.userservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import platform.eshop.plaza.userservice.dto.AuthResponseDTO;
import platform.eshop.plaza.userservice.dto.LoginRequestDTO;
import platform.eshop.plaza.userservice.dto.RegisterRequestDTO;
import platform.eshop.plaza.userservice.dto.UserResponseDTO;
import platform.eshop.plaza.userservice.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        UserResponseDTO created = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);

    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        AuthResponseDTO response = userService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}