package com.crediexpress.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Lista blanca de usuarios del negocio (docs §1.4). No es un rol: solo responde "¿este usuario es del
 * negocio?". Un token válido de Firebase cuyo UID ({@code sub}) no esté en la lista recibe 403.
 * Sin token sigue siendo 401 (lo responde Spring Security antes de llegar aquí).
 *
 * <p>Si la lista está vacía se niega a todos: un error de configuración nunca abre el acceso.
 */
@Slf4j
public class UsuariosPermitidos implements AuthorizationManager<RequestAuthorizationContext> {

    private final Set<String> uids;

    /** @param lista UIDs separados por coma, punto y coma o espacios. */
    public UsuariosPermitidos(String lista) {
        this.uids = Arrays.stream(lista == null ? new String[0] : lista.split("[,;\\s]+"))
                .map(String::trim)
                .filter(uid -> !uid.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (uids.isEmpty()) {
            log.warn("seguridad.usuarios-permitidos está vacío: se rechazarán todas las peticiones.");
        } else {
            log.info("Usuarios permitidos: {}", uids.size());
        }
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> autenticacion, RequestAuthorizationContext contexto) {
        Authentication auth = autenticacion.get();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }
        boolean permitido = uids.contains(auth.getName());
        if (!permitido) {
            log.warn("Acceso rechazado al UID {} ({} {})", auth.getName(),
                    contexto.getRequest().getMethod(), contexto.getRequest().getRequestURI());
        }
        return new AuthorizationDecision(permitido);
    }
}
