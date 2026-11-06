package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.config.SecurityConfig;
import co.edu.ucc.ritech.repository.UsuarioRepository;
import co.edu.ucc.ritech.service.CargaDocumentoService;
import co.edu.ucc.ritech.service.ExportacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ExportacionController.class)
@Import(SecurityConfig.class)
class DocumentoUploadSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ExportacionService exportaciones;
    @MockBean CargaDocumentoService cargaDocumento;
    @MockBean UsuarioRepository usuarios;

    @Test
    @WithMockUser(username = "exportaciones@ritech.example", roles = "ESPECIALISTA_EXPORTACION")
    void especialistaPuedeCargarConCsrfYRecibeVersion() throws Exception {
        when(cargaDocumento.cargar(eq(10L), eq(20L), any())).thenReturn(Map.of("numero_version", 2, "vigente", true));

        mvc.perform(multipart("/api/exportaciones/10/requisitos/20/documentos")
                        .file(new MockMultipartFile("file", "soporte.txt", "text/plain", "contenido valido".getBytes()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero_version").value(2))
                .andExpect(jsonPath("$.vigente").value(true));

        verify(cargaDocumento).cargar(eq(10L), eq(20L), any());
    }

    @Test
    @WithMockUser(roles = "AUDITOR_PROCESO")
    void rolNoAutorizadoRecibe403() throws Exception {
        mvc.perform(multipart("/api/exportaciones/10/requisitos/20/documentos")
                        .file(new MockMultipartFile("file", "soporte.txt", "text/plain", "contenido valido".getBytes()))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cargaDocumento);
    }

    @Test
    @WithMockUser(roles = "ESPECIALISTA_EXPORTACION")
    void especialistaSinTokenCsrfRecibe403AntesDeCargar() throws Exception {
        mvc.perform(multipart("/api/exportaciones/10/requisitos/20/documentos")
                        .file(new MockMultipartFile("file", "soporte.txt", "text/plain", "contenido valido".getBytes())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cargaDocumento);
    }
}
