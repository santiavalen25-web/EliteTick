[readme.md](https://github.com/user-attachments/files/34592524/readme.md)
# 🎟️ EliteTick — Aplicación Nativa Android (Kotlin) + MySQL (XAMPP)

Proyecto para la materia de **Desarrollo de Aplicaciones Nativas**, basado en la propuesta de boletería de [nicolas547/elitetick2](https://github.com/nicolas547/elitetick2) y [nicolas547/elitetick](https://github.com/nicolas547/elitetick).

---

## 📋 Características del Proyecto

* **Autenticación y Registro de Usuarios**: Inicio de sesión y creación de cuentas conectado a la tabla `usuarios` en MySQL.
* **Cartelera de Eventos en Tiempo Real**: Consulta de eventos desde la tabla `eventos` con buscador y filtros por categoría (*Conciertos*, *Deportes*, *Teatro y Comedia*, *Tecnología*).
* **Compra de Boletas con Transacciones SQL**: Selección de localidad (*General* o *VIP*) y cantidad de entradas, cálculo automático del total, generación de código único (`ETK-2026-XXXX`) en la tabla `boletas` y descuento automático de cupos en MySQL.
* **Mis Boletas y Cancelación de Reservas**: Historial de compras del usuario (`INNER JOIN` entre `boletas` y `eventos`) con opción de cancelar y liberar los cupos en la base de datos.
* **Publicación de Nuevos Eventos (CRUD)**: Formulario integrado en la app para insertar nuevos eventos directamente en MySQL.
* **Modo Respaldo Local Automático**: Si se ejecuta la app sin tener encendido XAMPP, el sistema activa datos locales de respaldo automáticamente para permitir probar todas las pantallas sin errores.

---

## 🛠️ Requisitos Previos para los Compañeros del Equipo

1. **Android Studio** (Ladybug / Koala / Meerkat o superior).
2. **XAMPP** instalado (con los servicios **Apache** y **MySQL** activos).

---

## 🚀 Pasos para Ejecutar el Proyecto

### 1. Importar la Base de Datos en XAMPP (`phpMyAdmin`)
1. Abre **XAMPP Control Panel** y dale **Start** a **Apache** y **MySQL**.
2. Entra desde el navegador a `http://localhost/phpmyadmin`.
3. Ve a la pestaña **Importar** (o **SQL**) y ejecuta el archivo `backend_xampp/elitetick_db.sql` (o `elitetick_db.sql`).
4. Esto creará automáticamente la base de datos `elitetick_db` con las tablas `usuarios`, `eventos` y `boletas` y datos iniciales de prueba.

### 2. Colocar la API PHP en `htdocs`
1. Ve a la carpeta `C:\xampp\htdocs\` en tu computador.
2. Crea una carpeta llamada `elitetick` (`C:\xampp\htdocs\elitetick\`).
3. Copia el archivo `api.php` dentro de esa carpeta para que quede en:
   `C:\xampp\htdocs\elitetick\api.php`
4. Abre `http://localhost/elitetick/api.php` en tu navegador para verificar que responda `"API EliteTick activa en XAMPP (MySQL conectado)"`.

### 3. Abrir y Ejecutar en Android Studio
1. Clona este repositorio o ábrelo desde Android Studio (**File > Open**).
2. Espera a que termine la sincronización de Gradle (**Gradle Sync**).
3. Ejecuta la aplicación (**Run 'app'** ▶️) en un Emulador de Android Studio:
   * Si usas el **Emulador de Android Studio**, la URL `http://10.0.2.2/elitetick/api.php` ya viene configurada en `MainActivity.kt` y se conecta automáticamente a tu XAMPP local.
   * Si usas un **celular físico por USB/Wi-Fi**, cambia `10.0.2.2` en la constante `BASE_API_URL` de `MainActivity.kt` por la dirección IPv4 Wi-Fi de tu computador (por ejemplo, `http://192.168.1.15/elitetick/api.php`).

---

## 🔑 Usuarios de Prueba Precargados

| Rol | Correo Electrónico | Contraseña |
| :--- | :--- | :--- |
| **Cliente** | `nicolas@elitetick.com` | `123456` |
| **Administrador** | `admin@elitetick.com` | `123456` |
