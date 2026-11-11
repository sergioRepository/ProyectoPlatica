# Estado de la Fase 2

Plan: [PLAN.md](PLAN.md). Orden: Paso 0 → 1 → 9 → 2 a 8.

Etiquetas: **IMPLEMENTADO** (el código está escrito), **PROBADO** (sus tests corrieron en verde) y **PENDIENTE**.

| Paso | Qué es | Estado | Notas |
|---|---|---|---|
| 0 | Configuración reutilizable: `PLAN.md`, subagentes, `.claude/CLAUDE.md`, este archivo | IMPLEMENTADO | No lleva tests |
| 1 | Nombres: «La vida no es un banano» y «Peel» | PROBADO | Backend: 222 tests y ruff en verde. Android: 159 tests y lint sin errores. Falta ver en un teléfono de 360 dp si `home_brand` cabe en una línea |
| 9 | Bloqueantes de Google Play: batería, divulgación de Accesibilidad, anti-autosabotaje dentro de la app, `allowBackup` | PENDIENTE | Después: versión 0.5.0, AAB y `PLAY_CONSOLE.md` |
| 2 | Configuración central de planes | PENDIENTE | |
| 3 | Backend: migración, cupos, facturación | PENDIENTE | Antes: confirmar que la base de producción está a salvo |
| 4 | Android: facturación y restauración | PENDIENTE | |
| 5 | Accesos y créditos en el bloqueo | PENDIENTE | |
| 6 | Revisión automática condicionada al plan | PENDIENTE | |
| 7 | Pantalla de planes y Ajustes | PENDIENTE | |
| 8 | Documentación: política de datos, Términos, README, informe | PENDIENTE | |

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
