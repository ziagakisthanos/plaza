package platform.eshop.plaza.userservice.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import platform.eshop.plaza.userservice.enums.Role;

@Document(collection = "users")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class User {
    @Id
    private String id;

    private String name;

    @Indexed(unique=true)
    private String email;

    private String password;

    private Role role;

    private String avatar;
}
