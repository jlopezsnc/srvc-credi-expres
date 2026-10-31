package com.crediexpress.dto.entity;

/** Un convenio nunca se borra: si se deshace, se cancela y queda en la bitácora. */
public enum EstatusConvenio {
    VIGENTE,
    CANCELADO
}
