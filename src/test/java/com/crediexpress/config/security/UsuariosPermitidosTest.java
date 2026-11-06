package com.crediexpress.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;

class UsuariosPermitidosTest {

    private static final RequestAuthorizationContext CONTEXTO =
            new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/operaciones-cliente/cliente-lst"));

    private static Authentication token(String uid) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(uid).build();
        return new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
    }

    private static boolean permite(UsuariosPermitidos lista, Authentication auth) {
        return lista.check(() -> auth, CONTEXTO).isGranted();
    }

    @Test
    void permiteSoloLosUidsDeLaLista() {
        UsuariosPermitidos lista = new UsuariosPermitidos(" uid-1, uid-2 ");

        assertThat(permite(lista, token("uid-1"))).isTrue();
        assertThat(permite(lista, token("uid-2"))).isTrue();
        assertThat(permite(lista, token("uid-3"))).isFalse();
        assertThat(permite(lista, token("UID-1"))).isFalse();
    }

    @Test
    void aceptaComaPuntoYComaOEspacios() {
        UsuariosPermitidos lista = new UsuariosPermitidos("uid-1;uid-2 uid-3");

        assertThat(permite(lista, token("uid-3"))).isTrue();
    }

    @Test
    void listaVaciaONulaNiegaATodos() {
        assertThat(permite(new UsuariosPermitidos(""), token("uid-1"))).isFalse();
        assertThat(permite(new UsuariosPermitidos(null), token("uid-1"))).isFalse();
        assertThat(permite(new UsuariosPermitidos(" , "), token(""))).isFalse();
    }

    @Test
    void anonimoNuncaPasa() {
        Authentication anonimo = new AnonymousAuthenticationToken(
                "llave", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(permite(new UsuariosPermitidos("anonymousUser"), anonimo)).isFalse();
        assertThat(permite(new UsuariosPermitidos("uid-1"), null)).isFalse();
    }
}
