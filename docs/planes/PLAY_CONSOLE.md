# Prueba cerrada en Google Play: pasos y datos que debes preparar

Para: Sergio. Versión que se sube: **0.5.0 (versionCode 5)**, sin facturación todavía.

Por qué ahora: tu cuenta de desarrollador es personal y nueva. Google exige una prueba cerrada con **12 testers inscritos durante 14 días seguidos** antes de dejarte pedir el acceso a producción ([requisito oficial](https://support.google.com/googleplay/android-developer/answer/14151465)). El plazo empieza cuando los 12 aceptan la invitación, no cuando subes el archivo.

Lo marcado «NO VERIFICADO» no lo pude confirmar en una fuente oficial; Play Console te dirá el valor exacto al llegar a ese paso.

## Lo que tienes que tener listo antes de empezar

| Qué | Para qué paso | Estado |
|---|---|---|
| AAB firmado: `android/app/build/outputs/bundle/release/app-release.aab` | 7 | Lo genera el Paso 9 |
| 12 correos de cuentas de Google de personas que instalarán la app | 6 | PENDIENTE (tú) |
| URL pública de la política de privacidad | 3 | PENDIENTE (tú): ver abajo |
| Correo de contacto del responsable, para la política y para la ficha | 2 y 3 | PENDIENTE (tú) |
| Vídeo corto de la divulgación de Accesibilidad, subido a YouTube o Drive con enlace | 4 | PENDIENTE (tú), se graba con la 0.5.0 |
| Nombre, descripción corta y descripción completa de la ficha | 2 | PENDIENTE (tú); abajo va un párrafo obligatorio |
| Icono, gráfico de funciones y capturas de pantalla | 2 | PENDIENTE (tú). Tamaños: NO VERIFICADO |

**Política de privacidad.** Hoy la app enlaza a la página de GitHub de `docs/politica-de-datos.md`. Google exige una URL pública, sin restricción por país y que no sea un PDF ([política de Datos de usuario](https://support.google.com/googleplay/android-developer/answer/10144311)). Sirve la de GitHub solo si el repositorio es público. Antes de enviarla a revisión hay que completar el contacto del responsable, que hoy dice «pendiente de completar», y pasar la revisión legal que el propio documento marca como pendiente.

## Decisión pendiente antes de enviar a revisión: «Mostrar sobre otras apps»

El manifest declara `SYSTEM_ALERT_WINDOW`, pero la app no dibuja ninguna ventana encima de otras. El permiso solo sirve de seguro para que la pantalla de bloqueo pueda abrirse desde segundo plano. El revisor de cumplimiento lo marcó como IMPORTANTE: Google puede verlo como un permiso sin uso real ([política de permisos](https://support.google.com/googleplay/android-developer/answer/9888170)). La regla exacta para este permiso: NO VERIFICADO.

Tienes dos caminos:

- **Quitarlo**, junto con el paso «Mostrar sobre otras apps» del asistente. Antes hay que probar en un teléfono que la pantalla de bloqueo sigue abriendo bien sin él.
- **Conservarlo** y explicar en la revisión que se usa para abrir la pantalla de bloqueo desde segundo plano.

No se cambió nada en la 0.5.0: decide antes de enviar la versión a revisión.

## 1. Crear la app

En Play Console, «Crear app»:

- Nombre: **La vida no es un banano**.
- Idioma predeterminado: español.
- Tipo: app. Precio: gratis. Las suscripciones y los créditos llegarán después como compras dentro de la app.
- Acepta las declaraciones de políticas y de leyes de exportación.

## 2. Ficha de Play Store

- Descripción corta y completa. La completa debe incluir un párrafo sobre Accesibilidad, porque la política exige documentar ese uso en la ficha ([política de permisos](https://support.google.com/googleplay/android-developer/answer/9888170)). Texto propuesto:

  > La vida no es un banano usa el servicio de accesibilidad de Android (AccessibilityService API) solo para detectar qué app pasa al frente y mostrar la pantalla de bloqueo en las apps y horarios que tú eliges. No lee el contenido de la pantalla ni lo que escribes.

- No prometas una frecuencia exacta para la revisión automática ni digas que la app impide desinstalarla o desactivarla.
- Gráficos: icono, gráfico de funciones y al menos dos capturas de teléfono. Tamaños y cantidades: NO VERIFICADO.

## 3. Contenido de la app (panel «Configura tu app»)

| Apartado | Qué responder | Dato que preparas tú |
|---|---|---|
| Política de privacidad | La URL pública | La URL definitiva |
| Acceso a la app | Toda la app es accesible sin credenciales: no hay cuentas | Instrucciones para el revisor: aceptar las autorizaciones, completar el onboarding, y en «Activa el bloqueo» verá la divulgación de Accesibilidad |
| Anuncios | No contiene anuncios | — |
| Clasificación de contenido | Cuestionario. Declara que la app genera texto con IA que puede incluir lenguaje soez e insultos si el usuario lo elige, y que muestra líneas de ayuda en crisis | Correo de contacto |
| Público objetivo | Solo mayores de 18 años. La app ya lo exige en la pantalla de autorizaciones | — |
| Seguridad de los datos | Ver el apartado 5 | Revisión legal |
| Declaración de Accesibilidad | Ver el apartado 4 | El vídeo |
| Apps de salud | NO VERIFICADO si el formulario es obligatorio. La app declara que no es un servicio médico | — |
| ID de publicidad | La app no lo usa. NO VERIFICADO si hay que declararlo aparte | — |

## 4. Declaración de Accesibilidad

Google permite usar la API de Accesibilidad sin ser una herramienta de accesibilidad, pero exige divulgación destacada, consentimiento y esta declaración ([ayuda oficial](https://support.google.com/googleplay/android-developer/answer/10964491)).

**Formulario.**

- ¿Para qué necesita la app la API? Un solo propósito: detectar la app en primer plano para mostrar una pantalla de bloqueo en las apps y horarios que el usuario configura.
- ¿Recoge o comparte datos personales con esa capacidad? Sí, uno: el nombre de la app bloqueada. Queda en el historial del teléfono. Sale del teléfono solo en funciones que el usuario autoriza: cuando pide tiempo, viaja con su razón para que la IA la evalúe; y si autorizó la analítica, se registra qué app se bloqueó, si salió o pidió tiempo y con qué resultado.
- Añade esta aclaración: el servicio aplica una regla fija («app elegida en primer plano dentro del horario: mostrar la pantalla de bloqueo»). La IA no maneja el servicio, no ejecuta gestos ni navega por el teléfono. La política prohíbe usar la API para acciones autónomas y permite la automatización por reglas.
- La app no impide desactivar el servicio ni desinstalarla: desde la 0.5.0 no tapa ninguna pantalla del sistema.
- No marques la app como herramienta de accesibilidad.

**Vídeo.** Corto, grabado con la 0.5.0, mostrando en este orden:

1. Abrir la app y llegar a «Activa el bloqueo».
2. La pantalla de divulgación completa, legible.
3. El flujo cuando el usuario toca «No acepto»: la app sigue sin bloqueo.
4. El flujo cuando toca «Acepto»: se abren los ajustes de Accesibilidad y activa el servicio.
5. La función principal: abrir una app bloqueada y ver la pantalla de bloqueo.
6. Recomendado: desactivar el servicio desde Ajustes sin ningún obstáculo.

Si cambia la forma en que la app usa la API, hay que enviar el formulario otra vez.

## 5. Seguridad de los datos

Obligatorio desde la prueba cerrada ([ayuda oficial](https://support.google.com/googleplay/android-developer/answer/10787469)). Propuesta, pendiente de tu revisión legal:

| Dato | Tipo en el formulario | ¿Opcional para el usuario? | Propósito |
|---|---|---|---|
| Identificador aleatorio de la instalación | Identificadores de dispositivo u otros | No | Funcionamiento de la app |
| Minutos de uso por app de las últimas 24 horas | Actividad en apps: apps instaladas y otras acciones | No (autorización obligatoria) | Funcionamiento de la app |
| Texto de las razones que escribe el usuario | Otro contenido generado por el usuario | Sí | Funcionamiento de la app |
| Perfil: apodo, metas, apps y horarios | Otro contenido generado por el usuario | No | Funcionamiento de la app |
| Eventos de uso de la app | Interacciones con la app | Sí | Analítica |
| Mensajes reportados | Otro contenido generado por el usuario | Sí | Funcionamiento de la app |

- Cifrado en tránsito: sí.
- El usuario puede pedir el borrado: sí, en Ajustes, «Borrar todos mis datos».
- **Decisiones que necesitan criterio legal:**
  - El perfil y las razones se envían al proveedor del modelo de IA. No cuenta como «compartir» solo si el proveedor los procesa por cuenta tuya y no los usa para fines propios. El documento de política dice que eso no está verificado; mientras no lo esté, decláralo como compartido.
  - Las razones pueden contener datos de salud. Si hay que declarar además «Información de salud»: NO VERIFICADO.
- Cuando llegue la facturación habrá que añadir «Historial de compras».

## 6. Crear la prueba cerrada y la lista de testers

1. «Pruebas» → «Prueba cerrada» → crea una pista o usa la que viene por defecto.
2. Elige los países. Como mínimo, Colombia.
3. Crea una lista de correos con las 12 cuentas de Google. Conviene invitar a alguno más por si alguien se retira: si bajas de 12, el plazo deja de contar.
4. Pide a cada tester que acepte la invitación desde el enlace de participación y que instale la app desde Play Store.

## 7. Subir la versión

1. En la pista, «Crear versión».
2. La primera vez, Play te pedirá activar la firma de apps de Play. Tu keystore actual pasa a ser la clave de subida. Guarda el keystore y sus contraseñas fuera del repositorio.
3. Sube `app-release.aab`.
4. Notas de la versión, en español.
5. «Revisar versión» y envía a revisión. La revisión de la prueba cerrada también comprueba las políticas: si rechaza algo de Accesibilidad, aparecerá aquí.

## 8. Durante los 14 días

- Los 12 testers deben seguir inscritos de forma continua.
- Puedes subir versiones nuevas a la misma pista. Si eso reinicia el plazo: NO VERIFICADO.
- Guarda los comentarios de los testers y los cambios que hagas: el formulario de producción los pregunta.

## 9. Pedir el acceso a producción

Cuando se cumpla el requisito, en el Panel aparece «Solicitar acceso a producción». Pregunta por tu prueba cerrada, por la app y por tu preparación para producción. La revisión suele tardar siete días o menos.

## Lo que esta versión no incluye

La 0.5.0 no trae Play Billing. Queda PENDIENTE para cuando terminen los pasos 2 a 8:

- suscripción `premium` con los base plans `monthly` y `semester`, y consumible `creditos_10`, con sus precios por país;
- período de gracia, suspensión de cuenta y pausa;
- tema de Pub/Sub para las notificaciones y cuenta de servicio con permisos de finanzas y de pedidos;
- testers de licencia;
- «Historial de compras» en Seguridad de los datos.

Para crear esos productos, Play exige haber subido antes una versión que incluya la librería de facturación. Se puede subir a la pista interna, que no tiene requisito de testers.
