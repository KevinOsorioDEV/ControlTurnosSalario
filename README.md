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
./gradlew :domain:test          # pruebas del cálculo y de las validaciones
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

Las reglas cambian por fecha y la app las resuelve según el día trabajado: un corte que cruza
un cambio paga cada hora con la regla vigente ese día.

### Hay que actualizar cada año

| Cuándo | Qué | Dónde |
|---|---|---|
| Diciembre–enero | Salario mínimo y auxilio de transporte del año (decretos) | `domain/.../LegalPay.kt` → `VALUES` |
| Diciembre–enero | Festivos nuevos o trasladados por ley | `domain/.../ColombiaHolidays.kt` |

### Ya programado (no requiere cambios)

Están en `domain/.../LaborRules.kt` con sus fechas; la app cambia sola el día indicado. Solo hay
que tocarlos si una ley nueva modifica estos valores.

| Qué | Cambios | Ley |
|---|---|---|
| Recargo dominical y festivo | 75 % → 80 % (1-07-2025) → 90 % (1-07-2026) → 100 % (1-07-2027) | Ley 2466 de 2025 |
| Divisor de horas del mes | 240 → 235 (15-07-2023) → 230 (15-07-2024) → 220 (15-07-2025) → 210 (15-07-2026) | Ley 2101 de 2021 |
| Inicio de la jornada nocturna | 9:00 p. m. → 7:00 p. m. (25-12-2025) | Ley 2466 de 2025 |

`LegalPay` tiene los valores desde 2020. Si falta el decreto de un año, la app usa el último
conocido y lo avisa en Liquidación; para fechas anteriores a 2020 usa los de 2020.
Después de cada cambio, agrega una prueba en `domain/src/test` con un caso del año nuevo.

## Reglas al registrar turnos

Están en `domain/.../ShiftValidation.kt` y aplican al marcar, al editar y al cerrar un turno:

- El almuerzo se compara con el turno **programado**, no con lo trabajado. Si se entra tarde o
  se sale temprano, el turno se puede cerrar y la nómina recorta el almuerzo a lo trabajado.
  Un almuerzo igual o mayor que el turno programado se rechaza desde la entrada.
- La salida no puede ser una hora que aún no llega, ni el turno durar 24 horas o más.
- La pregunta por horas extra compara por minutos, igual que el pago: unos segundos de más no
  cuentan como extra.
- Un turno marcado en una fecha cuyo tramo pagado cae en otro corte (por el margen de
  puntualidad) aparece en ese corte, en la lista y en el Excel.

Al restaurar una copia de seguridad se rechaza el archivo entero si está dañado: sin versión,
con turnos de ids repetidos, más de un turno abierto, salida antes de la entrada o minutos
negativos. Los historiales de salario y auxilio guardados se leen igual de estrictos.

## Base de datos

Los esquemas de Room se exportan a `app/schemas/` y **deben versionarse**: son la referencia para
escribir y probar migraciones. Cada cambio en `ShiftEntity` necesita subir la versión de
`AppDatabase` y una `Migration`.
