package com.vincent.authservice.security;

import com.vincent.authservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    @Test
    void exposesUserFieldsAndAuthorities() {
        CustomUserDetails details = new CustomUserDetails(TestUsers.vincent());

        assertThat(details.getUsername()).isEqualTo("vincent");
        assertThat(details.getPassword()).isEqualTo(TestUsers.PASSWORD_HASH);
        assertThat(details.getRole()).isEqualTo("USER");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
        assertThat(details.getAuthorities())
                .singleElement()
                .isEqualTo(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void reflectsDisabledAccount() {
        CustomUserDetails details = new CustomUserDetails(TestUsers.disabledUser());

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.getUsername()).isEqualTo("disabled");
    }
}
