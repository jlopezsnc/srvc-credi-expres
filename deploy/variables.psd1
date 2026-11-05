# Variables de despliegue en GCP. NO son secretos (los secretos van en Secret Manager),
# por eso este archivo sí se sube al repo.
@{
    # Proyecto de GCP = proyecto de Firebase. Debe ser el mismo: Firebase Hosting solo puede enviar
    # /api/** a un servicio de Cloud Run de su propio proyecto.
    ProyectoGcp       = 'credi-express-app'
    FirebaseProjectId = 'credi-express-app'

    # La e2-micro solo es gratis en us-west1, us-central1 o us-east1. Cloud Run, la VM y el bucket
    # de respaldos van en la misma región (y la VM en una zona de ella) para que el tráfico sea interno.
    Region            = 'us-central1'
    Zona              = 'us-central1-a'

    # Nombres de los recursos (se pueden dejar así).
    ServicioBack      = 'crediexpress-back'
    Vm                = 'crediexpress-mysql'
    BaseDatos         = 'prestamos'
    UsuarioBd         = 'crediexpress_app'
    # Debe ser único en todo Cloud Storage; si ya existe en otra cuenta, cambiarlo.
    BucketRespaldos   = 'credi-express-app-respaldos-bd'
}
