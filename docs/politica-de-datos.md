# Política de tratamiento de datos · La vida no es un banano

Última actualización: noviembre de 2026. Versión del texto de autorización: `2026-11-v2`.

<!-- TODO(sergio): este documento necesita revisión legal antes de distribuir el APK. -->

Esta política explica qué datos usa la app **La vida no es un banano**, para qué, dónde están, cuánto tiempo se guardan y cómo puedes consultarlos, corregirlos o borrarlos. Se escribe para cumplir la Ley 1581 de 2012 y el Decreto 1377 de 2013 de Colombia.

La app te ayuda a reducir el uso del celular. No es un servicio médico ni de salud mental, y no reemplaza a un profesional.

## Quién responde por tus datos

- **Responsable:** Sergio Daniel Flórez López.
- **Contacto:** <!-- TODO(sergio): correo y documento o dirección de contacto para consultas y reclamos. -->pendiente de completar.

## Qué autorizas

Cuando abres la app te pedimos cuatro autorizaciones, por separado y sin marcar:

| Autorización | ¿Es obligatoria? | Qué pasa si no la das |
|---|---|---|
| Uso del teléfono | Sí | La app no se conecta a su servidor |
| Procesamiento fuera de Colombia | Sí | La app no se conecta a su servidor |
| Texto de tus excusas | **No** | La app funciona igual: decide en el teléfono, sin juez de IA |
| Analítica de uso de la app | **No** | No se envía ningún evento |

El texto de tus excusas puede contener datos de salud, que son **datos sensibles**. No estás obligado a autorizar su tratamiento, y ninguna función básica de la app depende de que lo hagas.

Puedes cambiar las dos autorizaciones opcionales cuando quieras en **Ajustes > Privacidad**. Para retirar las dos obligatorias, usa **Borrar mis datos**.

Guardamos qué autorizaste, la versión del texto y la fecha.

## Qué datos se usan y para qué

| Dato | Para qué | Dónde queda |
|---|---|---|
| Perfil: nombre o apodo, metas, excusas típicas, apps y horarios, tono, temas vetados | Escribir tus mensajes de bloqueo | En tu teléfono. El servidor lo procesa y no lo guarda |
| Texto de tus excusas, si lo autorizas | Que el juez de IA (Peel) decida si te da tiempo | En tu teléfono. El servidor lo procesa y **no lo guarda** |
| Uso de las últimas 24 horas: qué apps y cuántos minutos | Que el agente decida si intervenir | El detalle no se guarda; en el servidor queda un resumen por app (`agent_events`) |
| Decisiones del agente y su motivo | Historial y confirmación de intervenciones | Servidor (`agent_events`) |
| Identificador aleatorio del dispositivo y respuesta a "¿Pagarías?" de versiones anteriores | Aplicar los topes diarios | Servidor (`devices`, `device_daily_usage`) |
| Autorizaciones | Demostrar lo que aceptaste | Servidor (`device_consents`) |
| Consumo de IA: tokens, costo, modelo, tiempo y estado de cada llamada, sin el texto | Medir costos | Servidor (`llm_usage`) |
| Eventos de uso de la app, si lo autorizas: bloqueos, salidas, veredictos y códigos, nunca el texto de tus excusas | Entender si la app sirve | Servidor (`app_events`) |
| Mensajes que reportas | Revisar contenido inapropiado de la IA | Servidor (`content_reports`) |
| Compras de suscripción en Google Play: código de la compra, producto, plan, estado, fecha de vencimiento, renovación automática, compra que reemplaza y el código seudonimizado de tu instalación | Comprobar con Google que pagaste y darte los beneficios de tu plan | Servidor (`entitlements`) |
| Registro de créditos: compras de paquetes, accesos de 10 minutos pagados, traslados y reembolsos | Llevar tu saldo y evitar que una misma compra se canjee dos veces | Servidor (`credit_ledger`) |

No pedimos tu correo, tu número de teléfono ni tu ubicación.

**Pagos.** Los cobros los hace Google Play con tu cuenta de Google. No recibimos ni guardamos los datos de tu tarjeta o medio de pago. A Google le enviamos un código seudonimizado (un hash) del identificador de tu teléfono, nunca el identificador en claro, para asociar la compra a tu instalación. Con el código de la compra consultamos a Google si está pagada y vigente. El permiso de Accesibilidad solo detecta qué app pasa al frente: no lee la pantalla ni lo que escribes.

El historial de intentos y excusas vive en tu teléfono. Se borra con **Borrar mis datos** o al desinstalar la app.

## Cuánto tiempo se guardan

| Dato | Plazo |
|---|---|
| Texto de excusas y perfil en el servidor | No se guardan |
| `llm_usage`, `app_events`, `agent_events` | 90 días |
| Mensajes reportados (`content_reports`) | 180 días |
| Contadores diarios (`device_daily_usage`) | 7 días |
| Compras de suscripción (`entitlements`) | Hasta que pidas borrar tus datos |
| Registro de créditos (`credit_ledger`) | Se conserva anonimizado (ver «Borrar») |
| Dispositivo y autorizaciones | Hasta que pidas borrarlos |

Una tarea automática diaria aplica estos plazos.

## Con quién se comparten y dónde se procesan

Tus datos se procesan fuera de Colombia con estos proveedores:

| Proveedor | Para qué | Qué recibe |
|---|---|---|
| Vercel | Aloja el servidor | Todo lo que la app envía |
| Neon | Base de datos PostgreSQL | Lo que aparece como "Servidor" en esta política |
| PLATICA MX (gateway del Reto Agente 2026) | Conecta el servidor con el modelo de IA | Perfil, excusas autorizadas y uso |
| xAI (Grok) | Modelo de IA | Perfil, excusas autorizadas y uso |
| Google (Google Play) | Cobra los planes y los créditos y confirma las compras | Tu compra, asociada a tu cuenta de Google y a un código irreversible de tu instalación |

<!-- TODO(sergio): la pantalla de autorizaciones dice "servidores en Estados Unidos y México"; confirmarlo antes de distribuir. Confirmar países y regiones de Vercel y Neon, y qué retiene cada proveedor (sobre todo PLATICA MX y xAI). No está verificado: el servidor no guarda las excusas, pero eso no dice nada de lo que retengan ellos. -->

Los proveedores de IA ven el texto que se les envía para responder. **Qué conserven y por cuánto tiempo depende de sus propias políticas.** No escribas en tus excusas nada que no quieras enviarles.

No vendemos tus datos ni los usamos para publicidad.

## Tus derechos

Como titular de los datos puedes conocerlos, actualizarlos, corregirlos, pedir que se borren, revocar tu autorización y pedir prueba de ella. También puedes presentar quejas ante la Superintendencia de Industria y Comercio.

- **Borrar:** en **Ajustes > Privacidad > Borrar mis datos**. Borra de inmediato tus datos del servidor y del teléfono, incluidas tus autorizaciones y tus compras de suscripción registradas. La acción no se puede deshacer.
  - Pierdes tus créditos, tengas plan o no. El registro de créditos no se borra: se seudonimiza. Se quita el identificador de tu teléfono y las referencias quedan como códigos (hashes). Se conserva para que una misma compra no se pueda canjear otra vez.
  - Borrar tus datos **no cancela tu suscripción**. Cancélala en Google Play, en Pagos y suscripciones > Suscripciones.
  - Si reinstalas la app (sin usar «Borrar mis datos») con un plan activo, la suscripción se restaura desde Google Play con la misma cuenta de Google y tus créditos pasan a la instalación nueva. Sin plan, los créditos se pierden al desinstalar.
- **Corregir tu perfil:** en **Ajustes**.
- **Consultar o reclamar:** escribe al contacto de arriba. Las consultas se atienden en máximo 10 días hábiles y los reclamos en máximo 15 días hábiles.

<!-- TODO(sergio): confirmar con revisión legal que conservar el registro de créditos anonimizado tras el borrado es correcto, y que los datos de compra que procesa Google quedan bien descritos. -->

<!-- TODO(sergio): confirmar con revisión legal si borrar las autorizaciones al suprimir los datos es correcto, o si hay que conservar la prueba por un tiempo (Decreto 1377, art. 8). Hoy se borran porque así se pidió. -->

## Menores de edad

La app es para personas de 18 años o más. Al continuar en la pantalla de autorización declaras tener esa edad.

<!-- TODO(sergio): confirmar el tratamiento de menores con revisión legal si algún amigo o familiar lo es (Ley 1581, art. 7). -->

## Si estás pasando por un mal momento

Cuando escribes algo que parece una emergencia, la app te muestra líneas de ayuda. Esas líneas no son atendidas por esta app. Si estás en peligro, busca ayuda de inmediato.

## Cambios

Si esta política cambia de forma importante, la app te volverá a pedir tus autorizaciones.
