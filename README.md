<p align="center"><img src="docs/carta.svg" width="200" alt="VI · Los Enamorados"></p>

# VI · Los Enamorados

App Android que **habla directo con el smartwatch**, sin pasar por la app del fabricante. Funciona con los relojes genéricos que se usan con **HryFine** (`com.lianhezhuli.hyfit`): el reloj «Smartwatch» del proyecto Arcanos (firmware V2.2, dirección BLE `41:42:CE:02:D5:6C`).

Es la carta VI del mazo: el celular y el reloj, conversando.

## Qué hace (versión 0.1, experimental)

- **Encuentra y conecta el reloj** por Bluetooth de baja energía. Muestra primero los aparatos «conectados ahora» (si HryFine está conectado, el reloj deja de anunciarse, pero se le puede hablar por el mismo enlace), luego los emparejados y los que encuentre cerca. Marca con ★ los que anuncian el canal de HryFine.
- **Panel del reloj**: estado, batería y datos del fabricante (modelo, firmware…).
- **Probar el reloj**: vibrar (un pulso o 5 s), poner la hora del celular, enviar un mensaje con ícono (SMS, WhatsApp…), simular una llamada y colgarla, enviar el clima.
- **Botones de música**: en la pantalla de música del reloj, «anterior», «siguiente» y «pausa» controlan la música del celular (se puede apagar).
- **Comando a mano y consola**: arma cualquier trama del protocolo y muestra lo que se envía (→) y lo que responde el reloj (←) en hexadecimal. Sirve para descubrir qué más entiende este modelo. «Copiar» deja la consola lista para pegársela a Claude.
- **Registro**: como las demás apps de los Arcanos, mantener presionado el ícono → «Enviar registro».

## Cómo usarla

1. Instala el APK: [los-enamorados.apk](https://github.com/davidsant7e/los-enamorados-android/releases/download/ultima/los-enamorados.apk) (se compila solo en GitHub) o con `C:\Git\compilar-todo.bat`.
2. Ábrela y da el permiso de **Dispositivos cercanos**.
3. Elige el reloj (o escribe `41:42:CE:02:D5:6C`). La app recuerda tu elección y se conecta sola al abrirla.
4. Prueba **Vibrar**. Si el reloj vibra, el canal funciona; sigue con mensaje, hora, llamada y clima.

**HryFine puede quedarse instalado.** Android permite que dos apps hablen con el mismo reloj. Si algo no responde, cierra HryFine (Ajustes → Apps → HryFine → Forzar detención) y vuelve a conectar.

## El protocolo

Ingeniería inversa publicada por [jsphu/polosmart-pbs163-ble](https://github.com/jsphu/polosmart-pbs163-ble) (licencia MIT), a partir del APK de HryFine y de capturas Bluetooth. Detalle en `app/src/main/java/com/davidsant7e/enamorados/protocolo/Protocolo.kt`.

- Canal: Nordic UART con el último byte cambiado — servicio `6E400001-B5A3-F393-E0A9-E50E24DCCA9F`, RX `…0002…` (escribir), TX `…0003…` (avisos).
- Trama del celular: `DF | largo (2) | suma | categoría | 01 | subcomando | largo datos (2) | datos`, con `largo = datos + 5` y la suma de todos los demás bytes en la posición 3.
- Respuesta del reloj: `FD | largo | suma | categoría | subcomando | estado` (termina en `01` si salió bien).
- Comandos usados (categoría `02`): `01` hora, `0B` vibrar, `0C` interruptores de avisos, `11` llamada, `12` mensaje, `1A` 12/24 h, `1B` unidades, `1D` clima. Botones de música: categoría `0C`.
- La pantalla muestra unos 42 caracteres en 3 líneas, sin emojis; el byte `00` corta el texto (la app los limpia).

Ese trabajo se hizo con otro reloj de la misma familia (Polosmart PBS163). Este «Smartwatch» trae además llamadas por Bluetooth clásico, así que puede entender comandos de más o de menos: la consola existe para averiguarlo.

## Pruebas

```bash
./gradlew testDebugUnitTest
```

Prueban el armado de tramas (con valores calculados aparte), la hora empaquetada, la limpieza del texto, el partido de tramas largas y la lectura de respuestas y eventos que llegan en trozos.

## Proyecto

- Kotlin, Jetpack Compose, minSdk 26, compileSdk 37. Sin librerías de Bluetooth externas.
- Paquete `com.davidsant7e.enamorados`. Firma de prueba compartida (`firma/`), igual que las demás apps.
- Parte del mazo de los Arcanos: [davidsant7e](https://github.com/davidsant7e).
