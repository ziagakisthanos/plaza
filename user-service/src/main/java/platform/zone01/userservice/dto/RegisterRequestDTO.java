package platform.zone01.userservice.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import platform.zone01.userservice.enums.Role;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class RegisterRequestDTO {
    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-zA-Z0-9 ]*$")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters.")
    private String password;

    @NotNull
    private Role role;
}
