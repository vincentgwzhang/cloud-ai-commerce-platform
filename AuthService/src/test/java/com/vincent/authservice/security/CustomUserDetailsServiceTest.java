package com.vincent.authservice.security;

import com.vincent.authservice.repository.UserRepository;
import com.vincent.authservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CustomUserDetailsServiceTest {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void loadUserByUsername() {
        userRepository.save(TestUsers.vincent());

        UserDetails details = userDetailsService.loadUserByUsername("vincent");

        assertThat(details.getUsername()).isEqualTo("vincent");
        assertThat(details).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) details).getRole()).isEqualTo("USER");
    }

    @Test
    void unknownUserThrows() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
