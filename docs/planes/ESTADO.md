# Estado de la Fase 2

Plan: [PLAN.md](PLAN.md). Orden: Paso 0 → 1 → 9 → 2 a 8.

Etiquetas: **IMPLEMENTADO** (el código está escrito), **PROBADO** (sus tests corrieron en verde) y **PENDIENTE**.

| Paso | Qué es | Estado | Notas |
|---|---|---|---|
| 0 | Configuración reutilizable: `PLAN.md`, subagentes, `.claude/CLAUDE.md`, este archivo | IMPLEMENTADO | No lleva tests |
| 1 | Nombres: «La vida no es un banano» y «Peel» | PROBADO | Backend: 222 tests y ruff en verde. Android: 159 tests y lint sin errores. Falta ver en un teléfono de 360 dp si `home_brand` cabe en una línea |
| 9 | Bloqueantes de Google Play: batería, divulgación de Accesibilidad, anti-autosabotaje dentro de la app, `allowBackup` | PROBADO | Backend: 222 tests y ruff. Android: 158 tests y lint sin errores. Versión 0.5.0 (versionCode 5) y AAB de release firmado. No se probó en un teléfono. Guía: `PLAY_CONSOLE.md` |
| 2 | Configuración central de planes | PENDIENTE | |
| 3 | Backend: migración, cupos, facturación | PENDIENTE | Antes: confirmar que la base de producción está a salvo |
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
| `SYSTEM_ALERT_WINDOW` declarado sin que la app dibuje ventanas encima de otras | IMPORTANTE | PENDIENTE: decisión de Sergio, descrita en `PLAY_CONSOLE.md` |
| «En tu horario» no mencionaba las sesiones de enfoque | MENOR | IMPLEMENTADO |
| «No optimizar» depende del fabricante | MENOR | IMPLEMENTADO: texto genérico |
| Quien activa Accesibilidad directamente en los ajustes del sistema no ve la divulgación | MENOR | PENDIENTE: cerrarlo dejaría sin bloqueo a quienes ya usan la 0.4.0 hasta que acepten |
| El paso «Datos de uso» no repite que los minutos por app se envían al servidor | MENOR | PENDIENTE: hoy lo dice la pantalla de autorizaciones |

Revisado y correcto: la divulgación está en el flujo normal, exige una acción afirmativa y Atrás cuenta como rechazo; el servicio solo lee el paquete de la app en primer plano; no queda ningún uso de Accesibilidad que estorbe desactivar o desinstalar; no queda rastro del permiso de batería; `allowBackup="false"`.

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
