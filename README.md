# Consulta de inventario y pedidos

Aplicación Android en Kotlin para consultar la API de inventario y pedidos desde el teléfono. Se conecta directamente por HTTPS y requiere Internet.

## Requisitos

- Android Studio con JDK 21 y Android SDK 36.1.
- Teléfono con Android 8.0 (API 26) o superior.

## Ejecutar en el teléfono desde Android Studio

1. Abrí esta carpeta como proyecto y elegí **Sync Project with Gradle Files**.
2. En la lista de configuraciones de ejecución, seleccioná **androidApp**. Si aparece **Add Configuration**, creá una configuración de tipo **Android App** y elegí el módulo `androidApp`.
3. Seleccioná tu teléfono en la lista de dispositivos y presioná **Run ▶**.
4. En la app, pegá la API key en **Configuración**. Después abrí el menú ☰ para consultar productos, pedidos y el estado del servicio.

Para generar el APK de prueba sin instalarlo, ejecutá `./gradlew :androidApp:assembleDebug`. El archivo resultante estará en `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Seguridad y funcionamiento

La API key no está incluida en el código ni en el APK. La app cifra la clave con Android Keystore, la guarda en almacenamiento privado y la envía a la API mediante el encabezado `X-API-Key`. La app no permite copias de seguridad de esos datos. Podés quitar la clave desde **Configuración**.

El menú ofrece estado del servicio, listas de productos y pedidos con búsqueda por texto, detalle al tocar un elemento, y creación de pedidos. La búsqueda filtra los elementos que devuelve cada listado de la API. En el detalle de un pedido se muestran los productos, la cantidad pedida de cada uno y el total de unidades.

Para crear un pedido se ingresa el nombre del cliente (2 a 100 caracteres), se seleccionan entre 1 y 20 productos del catálogo y se indica una cantidad de 1 a 100 por producto. La app valida los campos, pide confirmación y envía `customer_name` junto con `items` (`product_id` y `quantity`) a la API. Crear un pedido aceptado descuenta inventario.

## Estructura

- `androidApp/src/main/kotlin/com/consultainventario/app/MainActivity.kt`: pantallas y menú.
- `androidApp/src/main/kotlin/com/consultainventario/app/ApiClient.kt`: consultas HTTPS.
- `androidApp/src/main/kotlin/com/consultainventario/app/ApiRecords.kt`: adapta los listados JSON a tarjetas y campos legibles.
- `androidApp/src/main/kotlin/com/consultainventario/app/KeyStoreStore.kt`: almacenamiento cifrado de la API key.

## Flujo para explicar el proyecto

1. `MainActivity` abre **Configuración** y construye el menú lateral con las operaciones disponibles.
2. La API key ingresada se cifra y se guarda en el teléfono mediante `KeyStoreStore`.
3. Al elegir una operación, `MainActivity` valida los datos y llama a `ApiClient` en un hilo secundario.
4. `ApiClient` hace la solicitud HTTPS, agrega `X-API-Key` en las rutas protegidas y devuelve el código HTTP y el cuerpo.
5. `ApiRecords` convierte los listados JSON en elementos buscables; `MainActivity` muestra las listas y el detalle de cada elemento.
6. Al crear un pedido, `MainActivity` arma el cuerpo requerido por la API a partir del formulario y confirma la operación antes de enviarla.
