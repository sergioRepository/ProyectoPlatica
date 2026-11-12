# Estado de la Fase 2

Plan: [PLAN.md](PLAN.md). Orden: Paso 0 → 1 → 9 → 2 a 8.

Etiquetas: **IMPLEMENTADO** (el código está escrito), **PROBADO** (sus tests corrieron en verde) y **PENDIENTE**.

| Paso | Qué es | Estado | Notas |
|---|---|---|---|
| 0 | Configuración reutilizable: `PLAN.md`, subagentes, `.claude/CLAUDE.md`, este archivo | IMPLEMENTADO | No lleva tests |
| 1 | Nombres: «La vida no es un banano» y «Peel» | PROBADO | Backend: 222 tests y ruff en verde. Android: 159 tests y lint sin errores. Falta ver en un teléfono de 360 dp si `home_brand` cabe en una línea |
| 9 | Bloqueantes de Google Play: batería, divulgación de Accesibilidad, anti-autosabotaje dentro de la app, `allowBackup` | PROBADO | Backend: 222 tests y ruff. Android: 158 tests y lint sin errores. Versión 0.5.1 (versionCode 6), sin `SYSTEM_ALERT_WINDOW`; AAB y APK de release firmados con la clave de release. No se probó en un teléfono: lista de pruebas abajo. Guía: `PLAY_CONSOLE.md` |
| 2 | Configuración central de planes | PROBADO | `PlanCatalog` (Android) y `Settings.benefits` (backend) con los mismos valores; sin cambio de comportamiento |
| 3 | Backend: migración, cupos, facturación | PENDIENTE | BLOQUEADO: Vercel crea un despliegue Preview por cada push a `play-store` (comprobado: commit `fef164c`). Falta confirmar que Preview no usa la base de producción |
| 4 | Android: facturación y restauración | PENDIENTE | |
| 5 | Accesos y créditos en el bloqueo | PENDIENTE | |
| 6 | Revisión automática condicionada al plan | PENDIENTE | |
| 7 | Pantalla de planes y Ajustes | PENDIENTE | |
| 8 | Documentación: política de datos, Términos, README, informe | PENDIENTE | |

## Revisión de cumplimiento del Paso 9 (`play-compliance-reviewer`)

Ningún bloqueante. Lo que marcó:

| Hallazgo | Severidad | Estado |
|---|---|---|
| La divulgación decía que el nombre de la app sale del teléfono «solo en dos casos»; la analítica también registra qué app se bloqueó | IMPORTANTE | IMPLEMENTADO: el texto ahora describe todo lo que sale y solo lo que detecta el servicio |
| `SYSTEM_ALERT_WINDOW` declarado sin que la app dibuje ventanas encima de otras | IMPORTANTE | IMPLEMENTADO en la 0.5.1: fuera el permiso y el paso «Mostrar sobre otras apps». PENDIENTE: probar en el teléfono que el bloqueo abre igual (lista abajo) |
| «En tu horario» no mencionaba las sesiones de enfoque | MENOR | IMPLEMENTADO |
| «No optimizar» depende del fabricante | MENOR | IMPLEMENTADO: texto genérico |
| Quien activa Accesibilidad directamente en los ajustes del sistema no ve la divulgación | MENOR | PENDIENTE: cerrarlo dejaría sin bloqueo a quienes ya usan la 0.4.0 hasta que acepten |
| El paso «Datos de uso» no repite que los minutos por app se envían al servidor | MENOR | PENDIENTE: hoy lo dice la pantalla de autorizaciones |

Revisado y correcto: la divulgación está en el flujo normal, exige una acción afirmativa y Atrás cuenta como rechazo; el servicio solo lee el paquete de la app en primer plano; no queda ningún uso de Accesibilidad que estorbe desactivar o desinstalar; no queda rastro del permiso de batería; `allowBackup="false"`.

## Pruebas de la 0.5.1 en el teléfono (PROBADO por Sergio el 2026-11-11)

Instalar el APK de release `android/app/build/outputs/apk/release/app-release.apk`. Si la 0.4.0 está instalada con otra firma, hay que desinstalarla antes. TikTok debe estar en la lista de apps bloqueadas.

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | En «Activa el bloqueo», tocar el paso de Accesibilidad y luego **Atrás** | Vuelve al asistente con el aviso de que no se activó. No se abren los ajustes |
| 2 | Tocar el paso otra vez y **No acepto** | Igual que la prueba 1 |
| 3 | Tocar el paso otra vez y **Acepto** | Se abren los ajustes de Accesibilidad; al activar el servicio, el asistente lo marca como listo |
| 4 | El asistente ya no muestra «Mostrar sobre otras apps» | Solo quedan Notificaciones, Batería y Datos de uso |
| 5 | Abrir TikTok **dentro del horario** de bloqueo | Aparece la pantalla de bloqueo, sin que TikTok se vea antes más de un instante |
| 6 | Dejar TikTok abierto **antes** de que empiece el horario y esperar a que empiece | La pantalla de bloqueo aparece sola al llegar la hora |
| 7 | Con TikTok al frente en horario, **apagar y encender la pantalla** | Al desbloquear el teléfono aparece la pantalla de bloqueo |
| 8 | **Reiniciar** el teléfono y abrir TikTok en horario, sin abrir antes La vida no es un banano | Aparece la pantalla de bloqueo |
| 9 | Fuera del horario, en Inicio tocar **«Revisar ahora»** hasta que el agente inicie una sesión de enfoque, y abrir TikTok | Aparece la pantalla de bloqueo durante la sesión |
| 10 | Pedir 10 minutos, que se apruebe, usar TikTok y esperar a que venza | Al vencer, la pantalla de bloqueo vuelve a aparecer sola |
| 11 | En Ajustes, en horario de bloqueo y con la protección encendida, intentar apagarla | Pide la frase «Soy consciente de que estoy apagando la protección»; con la frase, se apaga; con otro texto, el botón sigue apagado |
| 12 | Desactivar el servicio desde los ajustes de Accesibilidad del sistema y desinstalar la app | Nada lo estorba |

Las pruebas 5 a 10 son las que dependen del permiso retirado. Si alguna falla, se anota cuál y en qué teléfono y versión de Android, el permiso vuelve y se justifica en Play Console.

## Fallos que ya existían y se corrigieron para poder trabajar en verde

- **`test_android_strings_have_no_emojis` fallaba** desde el commit `d0e90e0`, que añadió las marcas ✓ y ✕ a `strings.xml`. El test trataba cualquier símbolo como emoji. Ahora admite esas dos marcas en los textos fijos de la app; lo que escribe la IA sigue sin símbolos. PROBADO.
- **`LocalTime.ofInstant` no existe antes de Android 12** y la app declara soporte desde Android 8: la notificación de desbloqueo, la de enfoque y la lista de desbloqueos de Inicio se caían en Android 8 a 11. Lint lo marcaba como error. Se cambió por `atZone(...).toLocalTime()` en `Notifier.kt` y `HomeViewModel.kt`. PROBADO con lint; no se probó en un teléfono con Android 8 a 11.

## Cómo se ejecutan los tests de Android en esta máquina

No hay `java` en el PATH. Gradle se lanza con el JDK de Android Studio solo para el comando:
`JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest lint`

## No se puede probar desde aquí

Compras reales, renovaciones, RTDN y cambio de plan. Requieren la app creada en Play Console, los productos dados de alta y testers de licencia.

## Nada está listo para Google Play

Queda pendiente todo lo de Play Console, la infraestructura y la revisión legal. El detalle estará en `PLAY_CONSOLE.md` y en `INFORME.md`.
