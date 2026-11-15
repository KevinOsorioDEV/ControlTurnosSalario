# Turnos

App Android para marcar entrada y salida y calcular lo que te van a pagar en Colombia:
recargos nocturnos y dominicales, horas extra, auxilio de transporte y descuentos de salud y
pensión. Todo se guarda solo en el teléfono.

## Estructura

| Módulo | Qué tiene |
|---|---|
| `domain/` | Kotlin puro, sin Android: reglas laborales, festivos, cálculo de nómina, Excel y copia de seguridad. Aquí van las pruebas. |
| `app/` | Interfaz en Jetpack Compose, Room (turnos), DataStore (ajustes), bloqueo con huella y recordatorio de salida. |

## Compilar y probar

```sh
./gradlew :domain:test          # pruebas del cálculo
./gradlew :app:assembleDebug    # APK de prueba
./gradlew :app:lintDebug        # revisión de la app
```

La integración continua (`.github/workflows/ci.yml`) ejecuta lo mismo en cada push.

## Publicar un APK

1. Sube `versionCode` (y `versionName`) en `app/build.gradle.kts`. Android no instala un APK con
   un `versionCode` igual o menor encima del que ya está.
2. Crea `keystore.properties` en la raíz (no se versiona):

   ```properties
   storeFile=turnos.jks
   storePassword=...
   keyAlias=turnos
   keyPassword=...
   ```

   Guarda el `.jks` y sus contraseñas fuera del repositorio y con copia: sin ellos no se puede
   actualizar la app ya instalada, habría que desinstalarla.
3. `./gradlew :app:assembleRelease` y copia el APK de `app/build/outputs/apk/release/` a `dist/`.
4. Etiqueta la versión: `git tag v0.2.0`.

Si cambia la firma del APK (por ejemplo, de debug a release), Android pide desinstalar la app
y eso borra los datos. Antes, guarda una copia en **Ajustes → Tus datos**.

## Actualización de valores legales

Las reglas cambian por fecha y la app las resuelve según el día trabajado. Revisa cada año:

| Cuándo | Qué | Dónde |
|---|---|---|
| Diciembre–enero | Salario mínimo y auxilio de transporte del año (decretos) | `domain/.../LegalPay.kt` → `VALUES` |
| Diciembre–enero | Festivos nuevos o trasladados por ley | `domain/.../ColombiaHolidays.kt` |
| 1 de julio | Recargo dominical (Ley 2466 de 2025: 80 % → 90 % → 100 % en 2027) | `domain/.../LaborRules.kt` |
| 15 de julio | Jornada máxima (Ley 2101 de 2021: 230 → 220 → 210 h) | `domain/.../LaborRules.kt` |

Si falta el decreto de un año, la app usa el último conocido y lo avisa en Liquidación.
Después de cada cambio, agrega una prueba en `domain/src/test` con un caso del año nuevo.

## Base de datos

Los esquemas de Room se exportan a `app/schemas/` y **deben versionarse**: son la referencia para
escribir y probar migraciones. Cada cambio en `ShiftEntity` necesita subir la versión de
`AppDatabase` y una `Migration`.
