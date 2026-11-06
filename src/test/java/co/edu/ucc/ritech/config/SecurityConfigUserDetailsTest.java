package co.edu.ucc.ritech.config;

import co.edu.ucc.ritech.domain.*;
import co.edu.ucc.ritech.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Carga propia de usuarios: solo cuentas activas y rol de base de datos")
class SecurityConfigUserDetailsTest {
    @Mock UsuarioRepository usuarios;

    @Test void loadUser_whenActive_shouldKeepEncodedPasswordAndMapDatabaseRole() {
        Role role = new Role(); role.setCodigo("ESPECIALISTA_EXPORTACION");
        Usuario u = new Usuario(); u.setEmail("active@example.test"); u.setActivo(true);
        u.setPasswordHash("test-encoded-hash"); u.setRol(role);
        when(usuarios.findByEmailIgnoreCase("ACTIVE@example.test")).thenReturn(Optional.of(u));

        var result = new SecurityConfig().userDetailsService(usuarios).loadUserByUsername("ACTIVE@example.test");

        assertAll(() -> assertEquals("active@example.test", result.getUsername()),
                () -> assertEquals("test-encoded-hash", result.getPassword()), () -> assertTrue(result.isEnabled()),
                () -> assertEquals("ROLE_ESPECIALISTA_EXPORTACION", result.getAuthorities().iterator().next().getAuthority()));
    }

    @Test void loadUser_whenInactive_shouldRejectWithoutReturningCredentials() {
        Usuario u = new Usuario(); u.setActivo(false);
        when(usuarios.findByEmailIgnoreCase("inactive@example.test")).thenReturn(Optional.of(u));

        assertEquals("Usuario no encontrado", assertThrowsExactly(UsernameNotFoundException.class,
                () -> new SecurityConfig().userDetailsService(usuarios).loadUserByUsername("inactive@example.test")).getMessage());
    }

    @Test void loadUser_whenMissing_shouldThrowUsernameNotFound() {
        assertEquals("Usuario no encontrado", assertThrowsExactly(UsernameNotFoundException.class,
                () -> new SecurityConfig().userDetailsService(usuarios).loadUserByUsername("missing@example.test")).getMessage());
    }
}
