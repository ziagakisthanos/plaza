package platform.eshop.plaza.userservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import platform.eshop.plaza.userservice.dto.*;
import platform.eshop.plaza.userservice.entity.User;
import platform.eshop.plaza.userservice.exception.EmailAlreadyExistsException;
import platform.eshop.plaza.userservice.exception.InvalidCredentialsException;
import platform.eshop.plaza.userservice.exception.UserNotFoundException;
import platform.eshop.plaza.commonsecurity.jwt.JwtService;
import platform.eshop.plaza.userservice.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        User saved = userRepository.save(user);
        return toResponseDTO(saved);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getId(), user.getRole().name());
        return new AuthResponseDTO(token);
    }

    public UserResponseDTO getById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return toResponseDTO(user);
    }

    public UserResponseDTO updateProfile(UpdateProfileRequestDTO request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setName(request.getName());
        user.setAvatar(request.getAvatar());

        User saved = userRepository.save(user);
        return toResponseDTO(saved);
    }

    private UserResponseDTO toResponseDTO(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getAvatar()
        );
    }

}
