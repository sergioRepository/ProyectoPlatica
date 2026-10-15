# La vida no es un banano

Bloqueador de apps para Android con una IA de sarcasmo personalizado. Cuando abres una app bloqueada en horario restringido, aparece una pantalla con un mensaje hecho a tu medida y tres salidas: salir, emergencia o 10 minutos más. Las dos últimas las juzga la IA.

- `android/`: app en Kotlin con Jetpack Compose.
- `backend/`: `lavidanoesunbanano-api` (FastAPI, LangGraph y Grok), desplegado en Vercel.
- [SPEC.md](SPEC.md): producto, diseño y fases.
- [CLAUDE.md](CLAUDE.md): comandos de build y test, y convenciones.

App de uso personal: no se publica en Play Store.

## Instalar en el teléfono

**[Descargar el APK](https://www.mediafire.com/file/dyz4x8pszfndc5m/la-vida-no-es-un-banano.apk/file)** (versión 0.1.0, 11 MB). Usa el backend de producción en Vercel.

1. Ábrelo en el teléfono. Android pide permitir instalar apps desde el navegador o el gestor de archivos.
2. En Samsung, si el Bloqueador automático (Auto Blocker) está activo, apágalo para poder instalar.
3. Abre la app y responde el onboarding. Al final pide el código de registro del servidor.
4. En Inicio, entra a "Permisos y batería" y sigue la guía. Como se instaló desde un archivo, el primer paso en Android 13 o más reciente es Permitir ajustes restringidos.

Por USB: `adb install -r apk/la-vida-no-es-un-banano.apk`.

El APK está firmado con la llave de debug de la máquina que lo compiló. Para instalar uno compilado en otra máquina hay que desinstalar antes la versión anterior, y eso borra el perfil y el historial.
