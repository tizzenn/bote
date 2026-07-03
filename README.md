# Bote

Aplicación Android para administrar las cuentas de grupos de gente: crea una
ficha por evento, apunta quién paga cada cosa y en qué porcentaje se reparte,
y al final genera el informe con las transferencias mínimas para quedar en paz.

## Características

- **Fichas de evento** con foto-avatar, fecha, ubicación, título y descripción
  (estos tres últimos opcionales).
- **Dos modos por ficha**: *restringido* (solo el admin-creador modifica y
  añade apuntes) o *colaborativo* (todos pueden).
- **Asistentes** añadidos desde los contactos del teléfono o a mano
  (nombre, teléfono y email opcionales), con avatar de iniciales de color
  determinista. Se muestran en la ficha a medida que se agregan.
- **Incorporación tardía**: si ya hay gastos apuntados al añadir a alguien,
  la app pregunta si asume los costes desde el principio (solidario) o si su
  perfil empieza limpio desde ese momento.
- **Apuntes** (un bien o servicio pagado por una única persona) con
  categoría de icono elegible (restaurante, copas, regalo, supermercado,
  alojamiento, combustible, sustancias, efectivo, otros), importes
  presupuestado (opcional), gastado (obligatorio) y pagado (obligatorio para
  cerrar el apunte).
- **Reparto por faders**: columna que alterna entre `=` (partes iguales) y
  `%` (porcentajes); cada asistente tiene un fader deslizable y un pin para
  fijar su porcentaje, de modo que mover un fader redistribuye
  proporcionalmente solo los faders abiertos.
- **Candado y cuenta final**: al cerrar la cuenta se genera el informe
  definitivo con quién paga cuánto y a quién; cada usuario marca cuándo hace
  su pago. Al pagar tu parte, el evento desaparece de tu listado general;
  solo el creador lo sigue viendo hasta que todos liquiden y quede a cero.
- **Botón de calendario** para añadir el evento al calendario de Android
  (sin permisos, mediante la pantalla estándar del sistema).
- **Sincronización sin servidor**: el evento viaja como archivo JSON o como
  **código QR** (se escanea desde el otro móvil). Al importar se **fusiona
  apunte a apunte** por UUID: gana la versión modificada más recientemente,
  las marcas de pago nunca se pierden y los apuntes borrados no resucitan
  (lápidas de borrado).
- **Foto del tique** en cada apunte, para poder verificar los importes del
  informe (se guarda en local; no viaja en la sincronización).
- **Desglose por categorías** en el informe, con barras proporcionales.
- **Modo extorsión** (opcional, desactivado por defecto): botón «plata o
  plomo» en el informe que redacta el mensaje de cobro a cada deudor a
  partir de una plantilla editable con {nombre}, {importe} y {evento}.
- **Registro de actividad** por evento: quién se une, qué se apunta, qué
  pagos se marcan, cierres de cuenta y sincronizaciones. Viaja con el
  evento al exportarlo y se une sin duplicados entre dispositivos.
- **Ajustes**: tema blanco/negro/sistema, color principal y de acento
  elegibles (negro y rojo por defecto) y opciones de notificaciones
  (día del evento, cierre de cuenta y recordatorio de pagos pendientes).

## Sincronización automática en la nube (opcional)

Además del QR y el archivo, Bote puede sincronizar solo contra una tabla de
[Supabase](https://supabase.com) (capa gratuita de sobra para uso personal) o
cualquier PostgREST propio. La fusión sigue siendo local (misma lógica que el
QR), el servidor es solo un buzón.

1. Crea un proyecto gratuito en supabase.com.
2. En *SQL Editor*, ejecuta:

```sql
create table if not exists eventos_sync (
  uuid text primary key,
  datos jsonb not null,
  actualizado timestamptz not null default now()
);
alter table eventos_sync enable row level security;
create policy "acceso anon" on eventos_sync
  for all using (true) with check (true);
```

3. Copia la **URL del proyecto** y la **clave `anon`** (Settings → API).

**El servidor es por evento**, no de la app: cada grupo puede tener el suyo, así
que un mismo usuario puede estar en varios grupos en servidores distintos. Al
crear un evento eliges en la sección *Sincronización*:

- **Local** — sin nube (solo QR/archivo).
- **Por defecto** — usa el servidor que tengas guardado en Ajustes (para tu
  grupo habitual).
- **Otro** — pegas la URL y la clave de ese grupo concreto.

Lo configura **solo quien crea el evento**. La config del servidor **viaja
dentro del evento** al compartirlo (QR o archivo), así que los demás asistentes,
al importarlo, quedan conectados automáticamente sin teclear nada. A partir de
ahí, al abrir el evento la app baja la copia remota, la fusiona y sube el
resultado.

Aviso honesto: cualquiera que tenga la clave anon puede leer la tabla entera, así
que compártela solo con tu grupo (el uuid del evento hace de secreto, pero la
clave es la llave del buzón). Las fotos no se suben.

## Compilación

Proyecto Android estándar (Kotlin, Material Components, Room). Requiere
JDK 17 y el SDK de Android (compileSdk 34):

```
gradle assembleDebug
```

En GitHub Actions hay dos flujos: `build.yml` compila el APK de depuración en
cada push a `main`, y `release.yml` compila el APK firmado al etiquetar
`v*`, lo publica en el repositorio personal de F-Droid
(https://tizzenn.github.io/fdroid/repo) y crea la release de GitHub.

Secrets necesarios para publicar: `BOTE_KEYSTORE_B64`, `BOTE_KEYSTORE_PASS`,
`FDROID_DEPLOY_KEY`, `FDROID_KEYSTORE_B64` y `FDROID_KEYSTORE_PASS`.

## Licencia

GNU GPL v3: cualquier redistribución o derivado debe publicar su código
fuente bajo la misma licencia. Ver [LICENSE](LICENSE).
