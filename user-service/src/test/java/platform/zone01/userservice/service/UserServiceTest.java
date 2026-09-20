package platform.zone01.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import platform.zone01.commonsecurity.jwt.JwtService;
import platform.zone01.userservice.dto.AuthResponseDTO;
import platform.zone01.userservice.dto.LoginRequestDTO;
import platform.zone01.userservice.dto.RegisterRequestDTO;
import platform.zone01.userservice.dto.UpdateProfileRequestDTO;
import platform.zone01.userservice.dto.UserResponseDTO;
import platform.zone01.userservice.entity.User;
import platform.zone01.userservice.enums.Role;
import platform.zone01.userservice.exception.EmailAlreadyExistsException;
import platform.zone01.userservice.exception.InvalidCredentialsException;
import platform.zone01.userservice.exception.UserNotFoundException;
import platform.zone01.userservice.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private User existingUser() {
        return new User("user-1", "Alice", "alice@example.com", "hashed", Role.SELLER, "avatar-1");
    }

    @Test
    void register_savesUserWithEncodedPassword() {
        RegisterRequestDTO request = new RegisterRequestDTO("Alice", "alice@example.com", "secret1", Role.SELLER);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });

        UserResponseDTO result = userService.register(request);

        assertThat(result.getId()).isEqualTo("user-1");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getRole()).isEqualTo(Role.SELLER);
        verify(passwordEncoder).encode("secret1");
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        RegisterRequestDTO request = new RegisterRequestDTO("Alice", "alice@example.com", "secret1", Role.SELLER);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_returnsTokenWhenCredentialsMatch() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("secret1", "hashed")).thenReturn(true);
        when(jwtService.generateToken("user-1", "SELLER")).thenReturn("jwt-token");

        AuthResponseDTO result = userService.login(new LoginRequestDTO("alice@example.com", "secret1"));

        assertThat(result.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_throwsWhenPasswordDoesNotMatch() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(new LoginRequestDTO("alice@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_throwsWhenEmailIsUnknown() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequestDTO("nobody@example.com", "secret1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getById_returnsUser() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser()));

        UserResponseDTO result = userService.getById("user-1");

        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void getById_throwsWhenMissing() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById("missing"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateProfile_changesNameAndAvatar() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO result = userService.updateProfile(new UpdateProfileRequestDTO("Alicia", "avatar-2"), "user-1");

        assertThat(result.getName()).isEqualTo("Alicia");
        assertThat(result.getAvatar()).isEqualTo("avatar-2");
    }

    @Test
    void updateProfile_throwsWhenMissing() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(new UpdateProfileRequestDTO("Alicia", null), "missing"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
