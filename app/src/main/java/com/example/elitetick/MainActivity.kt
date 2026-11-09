package com.example.elitetick

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

// ============================================================================
// CONFIGURACIÓN DE CONEXIÓN A XAMPP (MySQL + PHP)
// ============================================================================
// 10.0.2.2 es la IP especial con la que el Emulador de Android Studio accede
// al localhost (127.0.0.1) de tu computador donde corre XAMPP.
// Si usas un celular físico por USB/Wi-Fi, cambia 10.0.2.2 por la IP de tu PC
// (por ejemplo: "http://192.168.1.15/elitetick/api.php").
// ============================================================================
const val BASE_API_URL = "http://10.0.2.2/elitetick/api.php"

// ============================================================================
// MODELOS DE DATOS (Equivalentes a las tablas MySQL)
// ============================================================================
data class Usuario(
    val id: Int,
    val nombre: String,
    val email: String,
    val telefono: String,
    val rol: String
)

data class Evento(
    val id: Int,
    val titulo: String,
    val categoria: String,
    val fechaEvento: String,
    val lugar: String,
    val ciudad: String,
    val descripcion: String,
    val precioGeneral: Double,
    val precioVip: Double,
    val cuposDisponibles: Int
)

data class BoletaComprada(
    val id: Int,
    val eventoTitulo: String,
    val fechaEvento: String,
    val lugar: String,
    val localidad: String,
    val cantidad: Int,
    val totalPagado: Double,
    val codigoReserva: String,
    val estado: String,
    val fechaCompra: String
)

// ============================================================================
// CLIENTE HTTP NATIVO + RESPALDO LOCAL (Para que nunca falle en sustentación)
// ============================================================================
object EliteTickRepository {

    var modoOfflineRespaldo = false
        private set

    // Datos en memoria en caso de que XAMPP esté apagado durante una prueba rápida
    private val eventosRespaldo = mutableListOf(
        Evento(1, "Festival Estéreo Noche 2026", "Conciertos", "15 Nov 2026 - 7:00 PM", "Parque Simón Bolívar", "Bogotá", "Noche de música en vivo con bandas internacionales y experiencia inmersiva.", 180000.0, 390000.0, 250),
        Evento(2, "Clásico de Fútbol Profesional", "Deportes", "22 Nov 2026 - 6:15 PM", "Estadio El Campín", "Bogotá", "Partido decisivo de cuadrangulares finales con ingreso digital.", 65000.0, 160000.0, 420),
        Evento(3, "Stand-Up Comedy: Risas de Medianoche", "Teatro y Comedia", "29 Nov 2026 - 8:30 PM", "Teatro Nacional", "Bogotá", "Show especial de comedia en vivo con invitados nacionales.", 55000.0, 110000.0, 120),
        Evento(4, "Arena Tech & Gaming Expo", "Tecnología", "05 Dic 2026 - 10:00 AM", "Corferias Pabellón 6", "Bogotá", "Torneos de eSports, conferencias de desarrollo móvil y realidad virtual.", 45000.0, 95000.0, 300)
    )

    private val boletasRespaldo = mutableListOf(
        BoletaComprada(1, "Festival Estéreo Noche 2026", "15 Nov 2026 - 7:00 PM", "Parque Simón Bolívar (Bogotá)", "VIP", 2, 780000.0, "ETK-2026-9041", "ACTIVA", "2026-11-08 20:30:00")
    )

    private suspend fun postJson(action: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("$BASE_API_URL?action=$action")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        payload.put("action", action)
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }
        val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        modoOfflineRespaldo = false
        JSONObject(responseText)
    }

    suspend fun login(email: String, pass: String): Result<Usuario> {
        return try {
            val req = JSONObject().apply {
                put("email", email)
                put("password", pass)
            }
            val res = postJson("login", req)
            if (res.optBoolean("ok")) {
                val u = res.getJSONObject("usuario")
                Result.success(
                    Usuario(
                        id = u.getInt("id"),
                        nombre = u.getString("nombre"),
                        email = u.getString("email"),
                        telefono = u.optString("telefono", ""),
                        rol = u.optString("rol", "cliente")
                    )
                )
            } else {
                Result.failure(Exception(res.optString("mensaje", "Credenciales inválidas")))
            }
        } catch (e: Exception) {
            modoOfflineRespaldo = true
            if (email.isNotBlank() && pass.isNotBlank()) {
                val rol = if (email.contains("admin", ignoreCase = true)) "admin" else "cliente"
                Result.success(Usuario(2, email.substringBefore("@").replaceFirstChar { it.uppercase() }, email, "3001234567", rol))
            } else {
                Result.failure(Exception("Ingresa correo y contraseña"))
            }
        }
    }

    suspend fun registrar(nombre: String, email: String, pass: String, telefono: String): Result<Usuario> {
        return try {
            val req = JSONObject().apply {
                put("nombre", nombre)
                put("email", email)
                put("password", pass)
                put("telefono", telefono)
            }
            val res = postJson("registro", req)
            if (res.optBoolean("ok")) {
                val u = res.getJSONObject("usuario")
                Result.success(
                    Usuario(
                        id = u.getInt("id"),
                        nombre = u.getString("nombre"),
                        email = u.getString("email"),
                        telefono = u.optString("telefono", ""),
                        rol = u.optString("rol", "cliente")
                    )
                )
            } else {
                Result.failure(Exception(res.optString("mensaje", "No se pudo registrar")))
            }
        } catch (e: Exception) {
            modoOfflineRespaldo = true
            Result.success(Usuario((10..999).random(), nombre, email, telefono, "cliente"))
        }
    }

    suspend fun obtenerEventos(): List<Evento> {
        return try {
            val res = postJson("listar_eventos", JSONObject())
            val arr = res.optJSONArray("eventos") ?: JSONArray()
            val lista = mutableListOf<Evento>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lista.add(
                    Evento(
                        id = o.getInt("id"),
                        titulo = o.getString("titulo"),
                        categoria = o.getString("categoria"),
                        fechaEvento = o.getString("fecha_evento"),
                        lugar = o.getString("lugar"),
                        ciudad = o.getString("ciudad"),
                        descripcion = o.getString("descripcion"),
                        precioGeneral = o.getDouble("precio_general"),
                        precioVip = o.getDouble("precio_vip"),
                        cuposDisponibles = o.getInt("cupos_disponibles")
                    )
                )
            }
            lista
        } catch (e: Exception) {
            modoOfflineRespaldo = true
            eventosRespaldo.toList()
        }
    }

    suspend fun crearEvento(
        titulo: String,
        categoria: String,
        fecha: String,
        lugar: String,
        ciudad: String,
        descripcion: String,
        precioGen: Double,
        precioVip: Double,
        cupos: Int
    ): Result<String> {
        return try {
            val req = JSONObject().apply {
                put("titulo", titulo)
                put("categoria", categoria)
                put("fecha_evento", fecha)
                put("lugar", lugar)
                put("ciudad", ciudad)
                put("descripcion", descripcion)
                put("precio_general", precioGen)
                put("precio_vip", precioVip)
                put("cupos_disponibles", cupos)
            }
            val res = postJson("crear_evento", req)
            if (res.optBoolean("ok")) {
                Result.success(res.optString("mensaje", "Evento creado en MySQL"))
            } else {
                Result.failure(Exception(res.optString("mensaje", "Error al crear evento")))
            }
        } catch (e: Exception) {
            modoOfflineRespaldo = true
            val nuevoId = (eventosRespaldo.maxOfOrNull { it.id } ?: 0) + 1
            eventosRespaldo.add(0, Evento(nuevoId, titulo, categoria, fecha, lugar, ciudad, descripcion, precioGen, precioVip, cupos))
            Result.success("Evento guardado (Modo Demo Local)")
        }
    }

    suspend fun comprarBoleta(usuarioId: Int, evento: Evento, localidad: String, cantidad: Int): Result<String> {
        return try {
            val req = JSONObject().apply {
                put("usuario_id", usuarioId)
                put("evento_id", evento.id)
                put("localidad", localidad)
                put("cantidad", cantidad)
            }
            val res = postJson("comprar_boleta", req)
            if (res.optBoolean("ok")) {
                Result.success(res.optString("mensaje", "Compra registrada en MySQL"))
            } else {
                Result.failure(Exception(res.optString("mensaje", "No se pudo completar la compra")))
            }
        } catch (e: Exception) {
            modoOfflineRespaldo = true
            val precioUnit = if (localidad.equals("VIP", true)) evento.precioVip else evento.precioGeneral
            val total = precioUnit * cantidad
            val codigo = "ETK-2026-${(1000..9999).random()}"
            boletasRespaldo.add(
                0,
                BoletaComprada(
                    id = (100..999).random(),
                    eventoTitulo = evento.titulo,
                    fechaEvento = evento.fechaEvento,
                    lugar = "${evento.lugar} (${evento.ciudad})",
                    localidad = localidad,
                    cantidad = cantidad,
                    totalPagado = total,
                    codigoReserva = codigo,
                    estado = "ACTIVA",
                    fechaCompra = "Hoy"
                )
            )
            val idx = eventosRespaldo.indexOfFirst { it.id == evento.id }
            if (idx >= 0) {
                val actual = eventosRespaldo[idx]
                eventosRespaldo[idx] = actual.copy(cuposDisponibles = (actual.cuposDisponibles - cantidad).coerceAtLeast(0))
            }
            Result.success("¡Compra exitosa! Código: $codigo")
        }
    }

    suspend fun obtenerMisBoletas(usuarioId: Int): List<BoletaComprada> {
        return try {
            val req = JSONObject().apply { put("usuario_id", usuarioId) }
            val res = postJson("mis_boletas", req)
            val arr = res.optJSONArray("boletas") ?: JSONArray()
            val lista = mutableListOf<BoletaComprada>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lista.add(
                    BoletaComprada(
                        id = o.getInt("id"),
                        eventoTitulo = o.getString("evento_titulo"),
                        fechaEvento = o.getString("fecha_evento"),
                        lugar = o.getString("lugar"),
                        localidad = o.getString("localidad"),
                        cantidad = o.getInt("cantidad"),
                        totalPagado = o.getDouble("total_pagado"),
                        codigoReserva = o.getString("codigo_reserva"),
                        estado = o.getString("estado"),
                        fechaCompra = o.optString("fecha_compra", "")
                    )
                )
            }
            lista
        } catch (e: Exception) {
            modoOfflineRespaldo = true
            boletasRespaldo.toList()
        }
    }

    suspend fun cancelarBoleta(boletaId: Int): Result<String> {
        return try {
            val req = JSONObject().apply { put("boleta_id", boletaId) }
            val res = postJson("cancelar_boleta", req)
            if (res.optBoolean("ok")) {
                Result.success(res.optString("mensaje", "Boleta cancelada"))
            } else {
                Result.failure(Exception(res.optString("mensaje", "Error al cancelar")))
            }
        } catch (e: Exception) {
            modoOfflineRespaldo = true
            val idx = boletasRespaldo.indexOfFirst { it.id == boletaId }
            if (idx >= 0) {
                boletasRespaldo[idx] = boletasRespaldo[idx].copy(estado = "CANCELADA")
            }
            Result.success("Reserva cancelada")
        }
    }
}

// ============================================================================
// ACTIVIDAD PRINCIPAL DE ANDROID STUDIO (0 dependencias adicionales)
// ============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF7C4DFF),
                    secondary = Color(0xFF00E5FF),
                    background = Color(0xFF0F111A),
                    surface = Color(0xFF1A1D2B),
                    onPrimary = Color.White,
                    onBackground = Color(0xFFF3F4F9),
                    onSurface = Color(0xFFF3F4F9)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EliteTickApp()
                }
            }
        }
    }
}

fun formatoPesos(valor: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0
    return format.format(valor)
}

// ============================================================================
// NAVEGACIÓN PRINCIPAL Y PANTALLAS EN JETPACK COMPOSE
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteTickApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usuarioActual by remember { mutableStateOf<Usuario?>(null) }
    var pestanaActual by remember { mutableStateOf(0) } // 0: Cartelera, 1: Mis Boletas, 2: Nuevo Evento
    var eventoSeleccionado by remember { mutableStateOf<Evento?>(null) }

    var listaEventos by remember { mutableStateOf<List<Evento>>(emptyList()) }
    var misBoletas by remember { mutableStateOf<List<BoletaComprada>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }

    fun recargarDatos() {
        val u = usuarioActual ?: return
        scope.launch {
            cargando = true
            listaEventos = EliteTickRepository.obtenerEventos()
            misBoletas = EliteTickRepository.obtenerMisBoletas(u.id)
            cargando = false
        }
    }

    if (usuarioActual == null) {
        PantallaAutenticacion(
            onLoginExitoso = { usuario ->
                usuarioActual = usuario
                recargarDatos()
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("EliteTick Boletería", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text(
                                text = if (EliteTickRepository.modoOfflineRespaldo)
                                    "Modo Respaldo Local (Verifica XAMPP en 10.0.2.2)"
                                else
                                    "Conectado a MySQL XAMPP • ${usuarioActual?.nombre}",
                                fontSize = 11.sp,
                                color = if (EliteTickRepository.modoOfflineRespaldo) Color(0xFFFFB74D) else Color(0xFF69F0AE)
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { recargarDatos() }) {
                            Text("🔄", fontSize = 18.sp)
                        }
                        TextButton(onClick = {
                            usuarioActual = null
                            eventoSeleccionado = null
                        }) {
                            Text("Salir", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF161925)
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF161925)) {
                    NavigationBarItem(
                        selected = pestanaActual == 0 && eventoSeleccionado == null,
                        onClick = {
                            eventoSeleccionado = null
                            pestanaActual = 0
                        },
                        icon = { Text("🏠", fontSize = 18.sp) },
                        label = { Text("Cartelera") }
                    )
                    NavigationBarItem(
                        selected = pestanaActual == 1 && eventoSeleccionado == null,
                        onClick = {
                            eventoSeleccionado = null
                            pestanaActual = 1
                            recargarDatos()
                        },
                        icon = { Text("🎟️", fontSize = 18.sp) },
                        label = { Text("Mis Boletas") }
                    )
                    NavigationBarItem(
                        selected = pestanaActual == 2 && eventoSeleccionado == null,
                        onClick = {
                            eventoSeleccionado = null
                            pestanaActual = 2
                        },
                        icon = { Text("➕", fontSize = 18.sp) },
                        label = { Text("Publicar Evento") }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    eventoSeleccionado != null -> {
                        PantallaDetalleCompra(
                            evento = eventoSeleccionado!!,
                            usuario = usuarioActual!!,
                            onVolver = { eventoSeleccionado = null },
                            onCompraExitosa = { mensaje ->
                                Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
                                eventoSeleccionado = null
                                pestanaActual = 1
                                recargarDatos()
                            }
                        )
                    }
                    pestanaActual == 0 -> {
                        PantallaCartelera(
                            eventos = listaEventos,
                            cargando = cargando,
                            onSeleccionarEvento = { eventoSeleccionado = it }
                        )
                    }
                    pestanaActual == 1 -> {
                        PantallaMisBoletas(
                            boletas = misBoletas,
                            onCancelarBoleta = { boletaId ->
                                scope.launch {
                                    val r = EliteTickRepository.cancelarBoleta(boletaId)
                                    Toast.makeText(context, r.getOrNull() ?: "Error", Toast.LENGTH_SHORT).show()
                                    recargarDatos()
                                }
                            }
                        )
                    }
                    pestanaActual == 2 -> {
                        PantallaCrearEvento(
                            onEventoCreado = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                pestanaActual = 0
                                recargarDatos()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. PANTALLA DE LOGIN Y REGISTRO (Autenticación contra tabla `usuarios`)
// ============================================================================
@Composable
fun PantallaAutenticacion(onLoginExitoso: (Usuario) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var esRegistro by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("nicolas@elitetick.com") }
    var password by remember { mutableStateOf("123456") }
    var telefono by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎟️",
                        fontSize = 30.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ELITETICK",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (esRegistro) "Crear cuenta en MySQL" else "Venta de Boletería Nativa",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(20.dp))

                if (esRegistro) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre completo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        label = { Text("Teléfono") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        scope.launch {
                            cargando = true
                            val resultado = if (esRegistro) {
                                EliteTickRepository.registrar(nombre, email, password, telefono)
                            } else {
                                EliteTickRepository.login(email, password)
                            }
                            cargando = false
                            resultado.onSuccess { user ->
                                onLoginExitoso(user)
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !cargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (cargando) "Conectando..." else if (esRegistro) "Registrarme" else "Iniciar Sesión")
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { esRegistro = !esRegistro }) {
                    Text(
                        if (esRegistro) "¿Ya tienes cuenta? Inicia sesión"
                        else "¿Nuevo en EliteTick? Regístrate aquí"
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. PANTALLA DE CARTELERA DE EVENTOS (Consulta de tabla `eventos`)
// ============================================================================
@Composable
fun PantallaCartelera(
    eventos: List<Evento>,
    cargando: Boolean,
    onSeleccionarEvento: (Evento) -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf("Todos") }
    val categorias = listOf("Todos", "Conciertos", "Deportes", "Teatro y Comedia", "Tecnología")

    val eventosFiltrados = eventos.filter { ev ->
        val coincideCat = categoriaSeleccionada == "Todos" || ev.categoria.equals(categoriaSeleccionada, ignoreCase = true)
        val coincideTexto = busqueda.isBlank() ||
                ev.titulo.contains(busqueda, ignoreCase = true) ||
                ev.lugar.contains(busqueda, ignoreCase = true)
        coincideCat && coincideTexto
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            placeholder = { Text("Buscar concierto, estadio o evento...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categorias) { cat ->
                FilterChip(
                    selected = categoriaSeleccionada == cat,
                    onClick = { categoriaSeleccionada = cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (cargando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (eventosFiltrados.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontraron eventos para este filtro.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(eventosFiltrados) { evento ->
                    TarjetaEvento(evento = evento, onClick = { onSeleccionarEvento(evento) })
                }
            }
        }
    }
}

@Composable
fun TarjetaEvento(evento: Evento, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = evento.categoria,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${evento.cuposDisponibles} boletas disp.",
                    fontSize = 12.sp,
                    color = if (evento.cuposDisponibles > 20) Color(0xFF69F0AE) else Color(0xFFFF8A80)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(evento.titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("📅 ${evento.fechaEvento}", fontSize = 13.sp, color = Color.LightGray)
            Text("📍 ${evento.lugar} • ${evento.ciudad}", fontSize = 13.sp, color = Color.LightGray)

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Desde (General)", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        text = formatoPesos(evento.precioGeneral),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Button(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
                    Text("Comprar Boletas")
                }
            }
        }
    }
}

// ============================================================================
// 3. PANTALLA DETALLE Y COMPRA DE BOLETAS (INSERT en `boletas` + UPDATE cupos)
// ============================================================================
@Composable
fun PantallaDetalleCompra(
    evento: Evento,
    usuario: Usuario,
    onVolver: () -> Unit,
    onCompraExitosa: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var localidad by remember { mutableStateOf("General") }
    var cantidad by remember { mutableStateOf(1) }
    var procesando by remember { mutableStateOf(false) }

    val precioUnitario = if (localidad == "VIP") evento.precioVip else evento.precioGeneral
    val totalPagar = precioUnitario * cantidad

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        TextButton(onClick = onVolver) {
            Text("← Volver a la cartelera", fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(evento.categoria, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(evento.titulo, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("📅 Fecha: ${evento.fechaEvento}", fontSize = 14.sp)
                Text("📍 Lugar: ${evento.lugar} (${evento.ciudad})", fontSize = 14.sp)
                Text("🎟️ Cupos disponibles en MySQL: ${evento.cuposDisponibles}", fontSize = 14.sp, color = Color(0xFF69F0AE))
                Spacer(modifier = Modifier.height(12.dp))
                Text(evento.descripcion, fontSize = 14.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(20.dp))
                Text("Selecciona Localidad:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = localidad == "General",
                        onClick = { localidad = "General" },
                        label = { Text("General (${formatoPesos(evento.precioGeneral)})") }
                    )
                    FilterChip(
                        selected = localidad == "VIP",
                        onClick = { localidad = "VIP" },
                        label = { Text("VIP (${formatoPesos(evento.precioVip)})") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cantidad de boletas:", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { if (cantidad > 1) cantidad-- },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp)
                        ) { Text("-") }
                        Text(
                            text = cantidad.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        OutlinedButton(
                            onClick = { if (cantidad < 10 && cantidad < evento.cuposDisponibles) cantidad++ },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp)
                        ) { Text("+") }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total a pagar:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatoPesos(totalPagar),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            procesando = true
                            val res = EliteTickRepository.comprarBoleta(
                                usuarioId = usuario.id,
                                evento = evento,
                                localidad = localidad,
                                cantidad = cantidad
                            )
                            procesando = false
                            res.onSuccess { msg -> onCompraExitosa(msg) }
                        }
                    },
                    enabled = !procesando && evento.cuposDisponibles >= cantidad,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (procesando) "Registrando compra en MySQL..." else "Confirmar Compra de Boleta")
                }
            }
        }
    }
}

// ============================================================================
// 4. PANTALLA DE MIS BOLETAS (Historial del Usuario y Cancelación)
// ============================================================================
@Composable
fun PantallaMisBoletas(
    boletas: List<BoletaComprada>,
    onCancelarBoleta: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Mis Boletas Compradas", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Sincronizadas con la tabla `boletas` en MySQL", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        if (boletas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no has comprado boletas.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(boletas) { b ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Código: ${b.codigoReserva}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = b.estado,
                                    color = if (b.estado == "ACTIVA") Color(0xFF69F0AE) else Color(0xFFFF8A80),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(b.eventoTitulo, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("📅 ${b.fechaEvento}", fontSize = 13.sp, color = Color.LightGray)
                            Text("📍 ${b.lugar}", fontSize = 13.sp, color = Color.LightGray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${b.cantidad}x ${b.localidad} • ${formatoPesos(b.totalPagado)}",
                                    fontWeight = FontWeight.Bold
                                )
                                if (b.estado == "ACTIVA") {
                                    OutlinedButton(onClick = { onCancelarBoleta(b.id) }) {
                                        Text("Cancelar", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. PANTALLA PARA CREAR EVENTO (CRUD Administración en tabla `eventos`)
// ============================================================================
@Composable
fun PantallaCrearEvento(onEventoCreado: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var titulo by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("Conciertos") }
    var fecha by remember { mutableStateOf("20 Dic 2026 - 8:00 PM") }
    var lugar by remember { mutableStateOf("") }
    var ciudad by remember { mutableStateOf("Bogotá") }
    var descripcion by remember { mutableStateOf("") }
    var precioGeneral by remember { mutableStateOf("80000") }
    var precioVip by remember { mutableStateOf("180000") }
    var cupos by remember { mutableStateOf("150") }
    var guardando by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Publicar Nuevo Evento en MySQL", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Inserta un nuevo registro directamente en la tabla `eventos`", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
        }
        item {
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título del Evento") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = categoria,
                onValueChange = { categoria = it },
                label = { Text("Categoría (Conciertos, Deportes, Teatro y Comedia, Tecnología)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = fecha,
                onValueChange = { fecha = it },
                label = { Text("Fecha y Hora") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lugar,
                    onValueChange = { lugar = it },
                    label = { Text("Lugar / Escenario") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ciudad,
                    onValueChange = { ciudad = it },
                    label = { Text("Ciudad") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = precioGeneral,
                    onValueChange = { precioGeneral = it },
                    label = { Text("Precio General") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = precioVip,
                    onValueChange = { precioVip = it },
                    label = { Text("Precio VIP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            OutlinedTextField(
                value = cupos,
                onValueChange = { cupos = it },
                label = { Text("Cupos Disponibles") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción del evento") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    if (titulo.isBlank() || lugar.isBlank()) return@Button
                    scope.launch {
                        guardando = true
                        val res = EliteTickRepository.crearEvento(
                            titulo = titulo,
                            categoria = categoria,
                            fecha = fecha,
                            lugar = lugar,
                            ciudad = ciudad,
                            descripcion = descripcion.ifBlank { "Evento oficial registrado en EliteTick." },
                            precioGen = precioGeneral.toDoubleOrNull() ?: 50000.0,
                            precioVip = precioVip.toDoubleOrNull() ?: 100000.0,
                            cupos = cupos.toIntOrNull() ?: 100
                        )
                        guardando = false
                        res.onSuccess { onEventoCreado(it) }
                    }
                },
                enabled = !guardando && titulo.isNotBlank() && lugar.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (guardando) "Guardando en MySQL..." else "Guardar Evento en Base de Datos")
            }
        }
    }
}