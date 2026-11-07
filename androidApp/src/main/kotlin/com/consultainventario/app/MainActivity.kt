package com.consultainventario.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Pantalla principal de la app. Crea la interfaz, maneja el menú lateral y
 * coordina las consultas entre [KeyStoreStore] y [ApiClient].
 * Las vistas se construyen en Kotlin, sin archivos XML de diseño.
 */
class MainActivity : Activity() {
    private data class OrderLine(
        var product: ApiRecord?,
        val quantity: EditText
    )

    private val navy = Color.rgb(18, 32, 52)
    private val blue = Color.rgb(48, 102, 211)
    private val muted = Color.rgb(106, 123, 144)
    private val background = Color.rgb(245, 247, 251)
    private val executor = Executors.newSingleThreadExecutor()
    private val api = ApiClient()
    private lateinit var keys: KeyStoreStore
    private lateinit var page: LinearLayout
    private lateinit var drawer: FrameLayout
    private lateinit var heading: TextView
    private var currentPage = "config"
    private var resultView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        keys = KeyStoreStore(this)
        buildShell()
        showPage("config")
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.visibility == View.VISIBLE) drawer.visibility = View.GONE
        else if (currentPage != "config") showPage("config")
        else super.onBackPressed()
    }

    /** Deja que la ventana reciba los insets para ubicar el contenido en el área segura. */
    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    /** Construye la barra superior, el área de contenido y el menú desplegable. */
    private fun buildShell() {
        // El fondo oscuro continúa detrás de las barras del sistema; los controles quedan dentro.
        val root = FrameLayout(this).apply { setBackgroundColor(navy) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(18), 0)
            setBackgroundColor(navy)
        }
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { drawer.visibility = View.VISIBLE }
        }
        top.addView(menu, LinearLayout.LayoutParams(dp(42), dp(58)))
        heading = TextView(this).apply {
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        top.addView(heading)
        content.addView(top, LinearLayout.LayoutParams(-1, dp(58)))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(40))
        }
        scroll.addView(page)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        drawer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { visibility = View.GONE }
        }
        val navigation = ScrollView(this).apply {
            setBackgroundColor(navy)
            setOnClickListener { /* Mantener abierto al tocar el menú. */ }
        }
        val navItems = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(20))
        }
        navigation.addView(navItems)
        drawer.addView(navigation, FrameLayout.LayoutParams(dp(278), -1, Gravity.START))
        addNavTitle(navItems, "CONSULTA INVENTARIO")
        addNavItem(navItems, "⚙  Configuración", "config")
        addNavGroup(navItems, "Servicio", listOf("Estado de la API" to "health"))
        addNavGroup(navItems, "Productos", listOf("Catálogo" to "products"))
        addNavGroup(navItems, "Pedidos", listOf("Mis pedidos" to "orders", "Crear pedido" to "create"))
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        applySafeArea(root)
        setContentView(root)
        root.requestApplyInsets()
    }

    /**
     * Reserva espacio para barras, recortes de pantalla y teclado sin usar márgenes fijos.
     * Los insets cambian según el teléfono, la orientación y el modo de navegación.
     */
    @Suppress("DEPRECATION")
    private fun applySafeArea(root: View) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val keyboard = insets.getInsets(WindowInsets.Type.ime())
                left = bars.left
                top = bars.top
                right = bars.right
                bottom = maxOf(bars.bottom, keyboard.bottom)
            } else {
                val cutout = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
                left = maxOf(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0)
                top = maxOf(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0)
                right = maxOf(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0)
                bottom = maxOf(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0)
            }
            view.setPadding(left, top, right, bottom)
            insets
        }
    }

    private fun addNavTitle(parent: LinearLayout, value: String) {
        parent.addView(text(value, 12f, Color.rgb(135, 161, 196), true).apply {
            letterSpacing = 0.12f
            setPadding(dp(10), dp(6), 0, dp(27))
        })
    }

    /** Agrega una categoría que se puede expandir o contraer en el menú lateral. */
    private fun addNavGroup(parent: LinearLayout, label: String, items: List<Pair<String, String>>) {
        val children = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.VISIBLE }
        val group = text("▾  $label", 15f, Color.WHITE, true).apply {
            setPadding(dp(11), dp(18), dp(8), dp(12))
            setOnClickListener {
                children.visibility = if (children.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                this.text = (if (children.visibility == View.VISIBLE) "▾  " else "▸  ") + label
            }
        }
        parent.addView(group)
        items.forEach { (title, id) -> addNavItem(children, title, id, true) }
        parent.addView(children)
    }

    private fun addNavItem(parent: LinearLayout, label: String, id: String, sub: Boolean = false) {
        parent.addView(text(label, 14f, Color.rgb(209, 221, 238)).apply {
            setPadding(dp(if (sub) 27 else 11), dp(12), dp(8), dp(12))
            setOnClickListener { drawer.visibility = View.GONE; showPage(id) }
        })
    }

    /** Sustituye el contenido visible según la opción seleccionada del menú. */
    private fun showPage(id: String) {
        currentPage = id
        page.removeAllViews()
        resultView = null
        when (id) {
            "config" -> showConfig()
            "health" -> showRequestPage(Route.HEALTH, "SERVICIO", "Comprueba si el servicio remoto está disponible. No necesita API key.")
            "products" -> showListPage(Route.PRODUCTS)
            "orders" -> showListPage(Route.ORDERS)
            "create" -> showCreateOrderPage()
        }
    }

    /** Solicita la API key y permite quitar la que ya está guardada. */
    private fun showConfig() {
        heading.text = "Configuración"
        pageTitle("PRIMER PASO", "Configuración", "Ingresá tu API key para conectar el teléfono con el servicio de inventario.")
        val card = card()
        card.addView(text("API key", 19f, navy, true))
        card.addView(text("Se guarda cifrada en este teléfono. No se incluye en el código de la app.", 13f, muted).apply { topMargin(dp(8)) })
        val input = field("Pegá aquí tu API key", true).apply { topMargin(dp(20)) }
        card.addView(input)
        card.addView(primaryButton("Guardar clave") {
            val value = input.text.toString().trim()
            if (!value.matches(Regex("[A-Za-z0-9_-]{16,256}"))) {
                input.error = "La clave debe tener entre 16 y 256 caracteres"
                return@primaryButton
            }
            try {
                keys.save(value)
                input.text.clear()
                toast("Clave guardada")
                showPage("config")
            } catch (_: Exception) { toast("No se pudo guardar la clave") }
        }.apply { topMargin(dp(14)) })
        val configured = keys.get() != null
        card.addView(text(if (configured) "●  Clave configurada" else "●  Sin configurar", 13f,
            if (configured) Color.rgb(36, 143, 97) else muted, true).apply { topMargin(dp(20)) })
        if (configured) card.addView(text("Quitar clave", 13f, blue, true).apply {
            setPadding(0, dp(18), 0, dp(5))
            setOnClickListener { keys.clear(); showPage("config") }
        })
        page.addView(card)
        page.addView(text("La app se conecta directamente por HTTPS a la API. Necesitás Internet en el teléfono.", 12f, muted).apply { topMargin(dp(18)) })
    }

    /** Carga productos o pedidos y permite buscarlos sin escribir parámetros de la API. */
    private fun showListPage(route: Route) {
        val pageId = if (route == Route.PRODUCTS) "products" else "orders"
        val isProducts = route == Route.PRODUCTS
        heading.text = if (isProducts) "Productos" else "Pedidos"
        pageTitle(
            if (isProducts) "PRODUCTOS" else "PEDIDOS",
            if (isProducts) "Catálogo" else "Mis pedidos",
            "Buscá en la lista y tocá un elemento para ver su detalle."
        )

        val search = field(if (isProducts) "Buscar producto, SKU o ID" else "Buscar pedido, estado o ID")
        page.addView(search)
        val count = text("Cargando…", 12f, muted).apply { topMargin(dp(18)) }
        page.addView(count)
        val items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(items)
        var records = emptyList<ApiRecord>()

        fun render() {
            val term = search.text.toString().trim().lowercase()
            val filtered = if (term.isEmpty()) records else records.filter { it.searchText.contains(term) }
            count.text = "${filtered.size} de ${records.size} ${if (isProducts) "productos" else "pedidos"}"
            items.removeAllViews()
            if (filtered.isEmpty()) {
                items.addView(text(if (records.isEmpty()) "No hay elementos para mostrar." else "No hay coincidencias.",
                    14f, muted).apply { topMargin(dp(20)) })
                return
            }
            filtered.forEach { record ->
                val item = card().apply {
                    topMargin(dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openDetail(route, record) }
                }
                item.addView(text(record.title, 16f, navy, true))
                if (record.summary.isNotBlank()) {
                    item.addView(text(record.summary, 12f, muted).apply { topMargin(dp(8)) })
                }
                item.addView(text("Ver detalle  ›", 12f, blue, true).apply { topMargin(dp(12)) })
                items.addView(item)
            }
        }

        fun load() {
            val key = try { keys.get() } catch (_: Exception) { null }
            if (key == null) {
                toast("Primero configurá tu API key")
                showPage("config")
                return
            }
            records = emptyList()
            count.text = "Cargando…"
            items.removeAllViews()
            executor.execute {
                val response = try { api.request(route, key) }
                catch (error: Exception) { ApiResult(0, "No se pudo conectar: ${error.localizedMessage ?: "error de red"}") }
                val parsed = if (response.status in 200..299) {
                    try { ApiRecords.list(response.body, route) } catch (_: Exception) { null }
                } else null
                runOnUiThread {
                    if (currentPage != pageId || items.parent == null) return@runOnUiThread
                    if (parsed == null) {
                        count.text = "No se pudo cargar la lista"
                        items.addView(text(
                            if (response.status in 200..299) "La API devolvió un formato de lista no reconocido."
                            else "HTTP ${response.status}: ${response.body.take(240)}",
                            13f, Color.rgb(179, 65, 47)
                        ).apply { topMargin(dp(16)) })
                    } else {
                        records = parsed
                        render()
                    }
                }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        page.addView(primaryButton("Actualizar lista") { load() }.apply { topMargin(dp(16)) }, page.indexOfChild(count))
        load()
    }

    /** Consulta el endpoint de detalle cuando hay ID; si falta, usa los datos del listado. */
    private fun openDetail(listRoute: Route, record: ApiRecord) {
        val id = record.id
        if (id == null || !id.matches(Regex("[1-9][0-9]*"))) {
            showDetailDialog(record.title, record.data)
            return
        }
        val route = if (listRoute == Route.PRODUCTS) Route.PRODUCT else Route.ORDER
        val pageId = if (listRoute == Route.PRODUCTS) "products" else "orders"
        toast("Cargando detalle…")
        val key = try { keys.get() } catch (_: Exception) { null }
        executor.execute {
            val detail = try {
                val response = api.request(route, key, id = id)
                if (response.status in 200..299) ApiRecords.detail(response.body, route) else null
            } catch (_: Exception) { null }
            runOnUiThread {
                if (currentPage == pageId) {
                    if (detail == null) toast("Mostrando los datos del listado")
                    showDetailDialog(record.title, detail ?: record.data)
                }
            }
        }
    }

    /** Presenta los campos del producto o pedido como datos legibles, sin JSON sin procesar. */
    private fun showDetailDialog(title: String, data: JSONObject) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(8))
        }
        val hasOrderItems = data.optJSONArray("items") != null
        ApiRecords.fields(data, if (hasOrderItems) setOf("items") else emptySet()).forEach { (name, value) ->
            content.addView(text(name, 12f, muted, true).apply { topMargin(dp(14)) })
            content.addView(text(value, 14f, navy).apply {
                topMargin(dp(4))
                setTextIsSelectable(true)
            })
        }
        if (hasOrderItems) {
            val items = ApiRecords.orderItems(data)
            val units = items.sumOf { it.quantity }
            val productLabel = if (items.size == 1) "producto diferente" else "productos diferentes"
            val unitLabel = if (units == 1) "unidad" else "unidades"
            content.addView(text("Productos pedidos", 15f, navy, true).apply { topMargin(dp(22)) })
            content.addView(text("${items.size} $productLabel · $units $unitLabel en total", 12f, muted)
                .apply { topMargin(dp(5)) })
            items.forEach { item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = rounded(Color.rgb(246, 249, 253), dp(9), Color.rgb(221, 231, 243))
                    topMargin(dp(12))
                }
                row.addView(text(item.name, 14f, navy, true))
                row.addView(text("Cantidad pedida: ${item.quantity}", 13f, blue, true).apply { topMargin(dp(6)) })
                if (item.unitPrice.isNotBlank()) {
                    row.addView(text("Precio unitario: ${item.unitPrice}", 12f, muted).apply { topMargin(dp(5)) })
                }
                if (item.lineTotal.isNotBlank()) {
                    row.addView(text("Subtotal: ${item.lineTotal}", 12f, muted).apply { topMargin(dp(4)) })
                }
                content.addView(row)
            }
        }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setPositiveButton("Cerrar", null).show()
    }

    /** Construye un pedido con nombre, productos y cantidades, sin pedir JSON al usuario. */
    private fun showCreateOrderPage() {
        heading.text = "Crear pedido"
        pageTitle("PEDIDOS", "Crear pedido", "Elegí los productos y sus cantidades. La API descontará inventario al confirmar.")
        val form = card()
        form.addView(label("Nombre del cliente"))
        val customer = field("Nombre y apellido").apply { filters = arrayOf(android.text.InputFilter.LengthFilter(100)) }
        form.addView(customer)
        val catalogStatus = text("Cargando productos…", 12f, muted).apply { topMargin(dp(22)) }
        form.addView(catalogStatus)
        val linesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(linesContainer)
        page.addView(form)

        val lines = mutableListOf<OrderLine>()
        var products = emptyList<ApiRecord>()

        fun addLine() {
            if (lines.size >= 20) { toast("Máximo 20 productos por pedido"); return }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(Color.rgb(246, 249, 253), dp(10), Color.rgb(221, 231, 243))
                topMargin(dp(14))
            }
            val selector = primaryButton("Elegir producto") { }
            row.addView(selector)
            row.addView(label("Cantidad (1 a 100)"))
            val quantity = field("Cantidad").apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("1")
            }
            row.addView(quantity)
            val remove = text("Quitar producto", 12f, blue, true).apply {
                setPadding(0, dp(15), 0, dp(4))
            }
            row.addView(remove)
            val line = OrderLine(null, quantity)
            selector.setOnClickListener {
                if (products.isEmpty()) { toast("Esperá a que cargue el catálogo"); return@setOnClickListener }
                val labels = products.map { product ->
                    val stock = product.data.optInt("stock", -1)
                    product.title + if (stock >= 0) " · Stock: $stock" else ""
                }.toTypedArray()
                AlertDialog.Builder(this).setTitle("Elegí un producto")
                    .setItems(labels) { _, index ->
                        line.product = products[index]
                        selector.text = products[index].title
                    }.show()
            }
            remove.setOnClickListener {
                if (lines.size > 1) {
                    lines.remove(line)
                    linesContainer.removeView(row)
                } else toast("El pedido necesita al menos un producto")
            }
            lines.add(line)
            linesContainer.addView(row)
        }

        addLine()
        val addButton = primaryButton("Agregar otro producto") { addLine() }.apply {
            topMargin(dp(18))
            isEnabled = false
        }
        form.addView(addButton)
        lateinit var submit: Button
        submit = primaryButton("Crear pedido") {
            val name = customer.text.toString().trim()
            if (name.length !in 2..100) { customer.error = "Escribí entre 2 y 100 caracteres"; return@primaryButton }
            val selected = mutableSetOf<String>()
            val items = JSONArray()
            for (line in lines) {
                val product = line.product
                val id = product?.id
                val productId = id?.toIntOrNull()?.takeIf { it > 0 }
                if (productId == null) {
                    toast("Elegí un producto en cada línea")
                    return@primaryButton
                }
                if (!selected.add(id)) { toast("No repitas el mismo producto"); return@primaryButton }
                val quantity = line.quantity.text.toString().toIntOrNull()
                if (quantity == null || quantity !in 1..100) {
                    line.quantity.error = "La cantidad debe estar entre 1 y 100"
                    return@primaryButton
                }
                val stock = product.data.optInt("stock", -1)
                if (stock >= 0 && quantity > stock) {
                    line.quantity.error = "Solo hay $stock unidades disponibles"
                    return@primaryButton
                }
                items.put(JSONObject().put("product_id", productId).put("quantity", quantity))
            }
            val payload = JSONObject().put("customer_name", name).put("items", items).toString()
            val summary = lines.joinToString("\n") { "• ${it.quantity.text} × ${it.product?.title}" }
            submit.isEnabled = false
            var confirmed = false
            AlertDialog.Builder(this).setTitle("Confirmar pedido")
                .setMessage("Cliente: $name\n\n$summary\n\nSi la API acepta el pedido, descontará inventario.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear") { _, _ -> confirmed = true; submitOrder(payload, submit) }
                .setOnDismissListener { if (!confirmed) submit.isEnabled = true }
                .show()
        }.apply { topMargin(dp(14)); isEnabled = false }
        form.addView(submit)

        val key = try { keys.get() } catch (_: Exception) { null }
        if (key == null) { toast("Primero configurá tu API key"); showPage("config"); return }
        executor.execute {
            val response = try { api.request(Route.PRODUCTS, key) }
            catch (error: Exception) { ApiResult(0, "No se pudo conectar: ${error.localizedMessage ?: "error de red"}") }
            val parsed = if (response.status in 200..299) {
                try { ApiRecords.list(response.body, Route.PRODUCTS) } catch (_: Exception) { null }
            } else null
            runOnUiThread {
                if (currentPage != "create" || form.parent == null) return@runOnUiThread
                products = parsed.orEmpty().filter { it.id?.matches(Regex("[1-9][0-9]*")) == true }
                catalogStatus.text = if (products.isEmpty()) "No se pudo cargar el catálogo. Volvé a abrir esta pantalla."
                    else "${products.size} productos disponibles"
                addButton.isEnabled = products.isNotEmpty()
                submit.isEnabled = products.isNotEmpty()
            }
        }
    }

    /** Envía el pedido una sola vez y convierte la respuesta en un mensaje legible. */
    private fun submitOrder(json: String, button: Button) {
        val key = try { keys.get() } catch (_: Exception) { null }
        if (key == null) { toast("Primero configurá tu API key"); showPage("config"); return }
        button.isEnabled = false
        button.text = "Creando pedido…"
        executor.execute {
            val response = try { api.request(Route.CREATE_ORDER, key, json = json) }
            catch (error: Exception) { ApiResult(0, "No se pudo conectar: ${error.localizedMessage ?: "error de red"}") }
            runOnUiThread {
                if (currentPage != "create") return@runOnUiThread
                button.isEnabled = true
                button.text = "Crear pedido"
                if (response.status in 200..299) {
                    val data = try { ApiRecords.detail(response.body, Route.ORDER) } catch (_: Exception) { null }
                    val id = data?.optString("id")?.takeIf { it.isNotBlank() }
                    AlertDialog.Builder(this).setTitle("Pedido creado")
                        .setMessage("La API registró el pedido${id?.let { " #$it" } ?: ""} y descontó el inventario.")
                        .setNegativeButton("Cerrar", null)
                        .setPositiveButton("Ver pedidos") { _, _ -> showPage("orders") }
                        .show()
                } else {
                    val message = try {
                        JSONObject(response.body).optJSONObject("error")?.optString("message")
                    } catch (_: Exception) { null }
                    AlertDialog.Builder(this).setTitle("No se pudo crear el pedido")
                        .setMessage(message?.takeIf { it.isNotBlank() } ?: response.body.take(300))
                        .setPositiveButton("Entendido", null).show()
                }
            }
        }
    }

    /** Muestra consultas públicas como el estado del servicio. */
    private fun showRequestPage(route: Route, section: String, description: String) {
        heading.text = route.title
        pageTitle(section, route.title, description)
        val card = card()
        card.addView(text("GET   /api/v1/${route.path}", 13f, blue, true))
        val action = primaryButton("Consultar") { execute(route) }.apply { topMargin(dp(22)) }
        card.addView(action)
        page.addView(card)
    }

    /**
     * Ejecuta la red fuera del hilo de interfaz para que la app siga respondiendo.
     * Al terminar, publica el resultado solo si el usuario sigue en la misma pantalla.
     */
    private fun execute(route: Route) {
        val key = try { keys.get() } catch (_: Exception) { null }
        if (route != Route.HEALTH && key == null) {
            toast("Primero configurá tu API key")
            showPage("config")
            return
        }
        val loading = text("Consultando…", 13f, muted).apply { topMargin(dp(22)) }
        page.addView(loading)
        executor.execute {
            val response = try { api.request(route, key) }
            catch (error: Exception) { ApiResult(0, "No se pudo conectar: ${error.localizedMessage ?: "error de red"}") }
            runOnUiThread {
                if (currentPage == routePageId(route)) {
                    page.removeView(loading)
                    resultView?.let { page.removeView(it) }
                    resultView = showResult(response)
                }
            }
        }
    }

    private fun routePageId(route: Route): String = when (route) {
        Route.HEALTH -> "health"
        Route.PRODUCTS -> "products"
        Route.PRODUCT -> "product"
        Route.ORDERS -> "orders"
        Route.ORDER -> "order"
        Route.CREATE_ORDER -> "create"
    }

    /** Presenta el código HTTP y formatea el JSON recibido para facilitar su lectura. */
    private fun showResult(response: ApiResult): View {
        val box = card().apply { topMargin(dp(20)) }
        val success = response.status in 200..299
        box.addView(text(if (success) "Respuesta · HTTP ${response.status}" else "Error · ${if (response.status == 0) "red" else "HTTP ${response.status}"}",
            14f, if (success) Color.rgb(35, 137, 92) else Color.rgb(179, 65, 47), true))
        val pretty = try { JSONObject(response.body).toString(2) }
            catch (_: Exception) { try { JSONArray(response.body).toString(2) } catch (_: Exception) { response.body } }
        box.addView(text(pretty, 12f, navy).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            topMargin(dp(16))
        })
        page.addView(box)
        return box
    }

    private fun pageTitle(section: String, title: String, description: String) {
        page.addView(text(section, 11f, blue, true).apply { letterSpacing = 0.16f })
        page.addView(text(title, 27f, navy, true).apply { topMargin(dp(10)) })
        page.addView(text(description, 14f, muted).apply { topMargin(dp(10)); bottomMargin(dp(24)) })
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = rounded(Color.WHITE, dp(14), Color.rgb(227, 233, 241))
        elevation = dp(2).toFloat()
    }

    private fun field(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
        setTextColor(navy)
        setHintTextColor(Color.rgb(152, 164, 180))
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setSingleLine(!hintText.startsWith("{"))
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = rounded(Color.WHITE, dp(8), Color.rgb(215, 225, 238))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun label(value: String): TextView = text(value, 12f, navy, true).apply { topMargin(dp(23)); bottomMargin(dp(9)) }

    private fun primaryButton(value: String, click: () -> Unit): Button = Button(this).apply {
        text = value
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(blue, dp(9))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { click() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun View.topMargin(value: Int) {
        val params = (layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(-1, -2)
        params.topMargin = value
        layoutParams = params
    }

    private fun View.bottomMargin(value: Int) {
        val params = (layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = value
        layoutParams = params
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
}
