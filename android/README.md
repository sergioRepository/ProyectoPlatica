# android

App Android de La vida no es un banano (módulo único `:app`). Desde la raíz del repositorio, con el JDK de Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
android\gradlew.bat -p android :app:assembleDebug       # APK debug (backend en localhost)
android\gradlew.bat -p android :app:testDebugUnitTest   # tests JVM
android\gradlew.bat -p android :app:assembleRelease     # APK contra el backend de producción
```
