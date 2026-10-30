# Variables de despliegue en GCP. NO son secretos (los secretos van en Secret Manager),
# por eso este archivo sí se sube al repo. Reemplazar cada 'CAMBIAR' antes de usar los scripts.
@{
    # ID del proyecto de GCP (Consola de GCP > selector de proyectos > columna "ID").
    ProyectoGcp       = 'CAMBIAR'

    # Región donde viven el clúster, Cloud SQL y Artifact Registry.
    Region            = 'us-central1'

    # Proyecto de Firebase. Si agregaste Firebase a tu proyecto de GCP, es el mismo ID.
    FirebaseProjectId = 'CAMBIAR'

    # Dominio público de la aplicación (debe apuntar a la IP fija que crea preparar-gcp.ps1).
    Dominio           = 'CAMBIAR'

    # Nombres de los recursos (se pueden dejar así).
    Cluster           = 'crediexpress'
    InstanciaSql      = 'crediexpress-db'
    BaseDatos         = 'prestamos'
    UsuarioBd         = 'crediexpress_app'
    Namespace         = 'default'
}
