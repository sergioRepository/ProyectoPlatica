# CLAUDE.md

Bloqueador de apps para Android con una IA sarcástica. El producto, las reglas de trabajo y las fases están en [SPEC.md](SPEC.md), que es la fuente de verdad.

## Contexto
- Uso personal: la app se instala por adb o APK y nunca va a Play Store.
- Teléfono de pruebas: Samsung Galaxy A14. Su último sistema es Android 15 (One UI 7); no recibe Android 16.
- Backend en producción: Vercel. Corre como una sola función de Python 3.12 y sin disco persistente, así que nada se guarda en archivos ni en SQLite. Docker y `docker-compose.yml` son solo para desarrollo local.
- Se trabaja por fases: plan, aprobación del usuario, implementación. Al cerrar cada fase: build, tests y cómo probarlo en el teléfono.

## Estructura
- `android/`: Gradle 9.5.1, AGP 9.1.1 y Kotlin 2.4.20 (el Kotlin integrado de AGP 9, sin plugin `kotlin-android`). Un solo módulo, `:app`, paquete `com.lavidanoesunbanano.app`.
- `backend/`: servicio `lavidanoesunbanano-api`, paquete Python `banano`, dependencias con uv. Vercel lo carga desde `[tool.vercel] entrypoint` en `pyproject.toml`.
- `docker-compose.yml`: backend local en `http://localhost:8000`.

## Comandos

### Android (PowerShell, desde la raíz)
No hay `java` en el PATH; se usa el JBR de Android Studio.
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
android\gradlew.bat -p android :app:assembleDebug       # APK debug
android\gradlew.bat -p android :app:testDebugUnitTest   # tests JVM
android\gradlew.bat -p android :app:installDebug        # instala en el teléfono conectado
adb reverse tcp:8000 tcp:8000                           # el teléfono ve el backend local en 127.0.0.1:8000
adb logcat --pid=$(adb shell pidof com.lavidanoesunbanano.app)
```
La URL del backend se cambia con `-Plavida.apiBaseUrl.debug=...` o `-Plavida.apiBaseUrl.release=...`, o en `android/gradle.properties`. No es un secreto. En debug solo se permite HTTP hacia `127.0.0.1`, `localhost` y `10.0.2.2` (`src/debug/res/xml/network_security_config.xml`).

### Backend (desde la raíz)
```powershell
uv --directory backend sync                                     # instala dependencias (Python 3.12)
uv --directory backend run pytest                               # tests, siempre con LLM falso
uv --directory backend run ruff check .                         # lint
uv --directory backend run ruff format .                        # formato
uv --directory backend run uvicorn banano.main:app --reload     # servidor local en :8000
docker compose up --build                                       # el mismo backend en Docker
```

## Convenciones
- **Cero emojis** en todo: UI, strings, mensajes de la IA, notificaciones y documentación. El backend filtra cualquier emoji que devuelva el LLM.
- **Secretos:** `XAI_API_KEY` vive solo en `backend/.env` (ignorado por git) o en las variables de Vercel. Nunca en Android, en git ni en logs. La URL del gateway y los nombres de modelo también llegan solo por entorno; no se escriben en el código.
- **Idioma:** los textos de la app van en español colombiano neutro, con frases cortas y voz activa, y siempre en `res/values/strings.xml`. Los identificadores de código van en inglés. Los nodos de LangGraph, los placeholders (`{nombre}`, `{meta}`...) y los campos JSON de la API usan los nombres del spec.
- **Android:** MVVM con repositorios y Hilt. La lógica de dominio va en Kotlin puro bajo `domain/`, con tests JVM. Las versiones solo se declaran en `android/gradle/libs.versions.toml`, y Gradle y AGP no se suben sin una razón concreta. Mientras la app esté en desarrollo, Room usa migración destructiva (ver `di/DatabaseModule.kt`).
- **Diseño:** los tokens están en `core/designsystem/Theme.kt`. Sin degradados ni sombras, bordes de 1 dp, radios de 4 a 8 dp y fundidos de 150 a 200 ms. Las fuentes Geist y Geist Mono se empaquetan en `res/font` (Fase 2), no se descargan.
- **Backend:** el LLM se inyecta y los tests nunca llaman a Grok real; eso lo hace el script de prueba de la Fase 1. El cliente es `ChatXAI` con `base_url` explícita, porque por su cuenta lee `XAI_API_BASE` y no `XAI_BASE_URL`. El límite de peticiones por dispositivo vive en memoria: en Vercel es aproximado porque cada instancia cuenta por separado, y el usuario lo aceptó así (sin Redis).
- **Git:** un commit al cerrar cada fase, en la rama `desarrollo`. Fuera de eso, solo cuando el usuario lo pida.

## Problemas conocidos
- **Bloqueador automático de Samsung (Auto Blocker):** si está activo, bloquea instalar APKs y comandos por USB. Hay que apagarlo para desarrollar.
- **Ajustes restringidos (Android 13+):** si el APK se instala desde un gestor de archivos, hay que permitirlos antes de activar Accesibilidad. Instalando por adb normalmente no aparece.
