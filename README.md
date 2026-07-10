# Bote

**Idioma:** Español · [English](README.en.md)

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

## Manual de uso

Bote lleva la cuenta de un grupo cuando se junta para algo (un finde, una cena,
un piso de vacaciones): quién pone dinero, en qué se lo gasta, cómo se reparte y,
al final, **quién le paga cuánto a quién** para que todos queden en paz con el
menor número de transferencias. No hace falta cuenta ni internet: cada móvil
lleva su copia y se pasan el evento entre ellos.

### La pantalla principal («Mis eventos»)

Lista de tus eventos. Cada tarjeta muestra la foto, el título, el número de
asistentes y el total gastado, y si está *Pendiente de liquidar* o *Saldado*.

- **Filtros**: *Activos*, *Saldados* o *Todos*.
- **Ordenar** (menú): por fecha, creación reciente, número de asistentes o los
  más caros.
- **`+`**: crear un evento nuevo.
- **Importar evento**: recibir uno que te pasan por archivo o por QR.

Cuando pagas tu parte de un evento, este desaparece de tu lista; el creador lo
sigue viendo hasta que **todos** hayan liquidado y quede a cero.

### Paso 1 — Crear el evento

| Campo | Para qué sirve |
|-------|----------------|
| **Foto** (opcional) | Un avatar para reconocer el evento de un vistazo. |
| **Título / Descripción** (opcionales) | «Finde en la sierra», y una nota si hace falta. |
| **Fecha** | El día del evento (obligatorio). |
| **Ubicación** (opcional) | Dirección o sitio; luego se puede abrir en el mapa. |
| **Modo** | *Restringido*: solo tú (el creador-admin) puedes modificar y añadir apuntes. *Colaborativo*: cualquiera del grupo puede. |
| **Sincronización** | Cómo viajará el evento entre móviles: *Local*, *Por defecto* u *Otro* (ver recuadro más abajo). |

**Asistentes**: añádelos *Desde contactos* o *A mano* (nombre y, opcionalmente,
teléfono y email). Cada uno recibe un avatar con sus iniciales y un color fijo.
Tú apareces como *Yo*.

> 🤓 **Dato avanzado — apuntar a alguien que llega tarde a la fiesta**
>
> Si añades a una persona cuando el evento **ya tiene gastos apuntados**, Bote te
> pregunta: ¿asume los gastos *desde el principio* o *solo desde ahora*?
> «Desde el principio» (solidario) la mete en el reparto de todo lo anterior,
> como si hubiera estado desde el minuto uno. «Solo desde ahora» le pone la
> cuenta a cero y solo entra en lo que se apunte a partir de ese momento —útil
> para el que se une el segundo día. No hace falta que entiendas la fórmula: solo
> decides si pagó su parte de lo de antes o no.

### Paso 2 — Apuntar los gastos («apuntes»)

Un **apunte** es una cosa que pagó **una sola persona** (la comida, el
alojamiento, la gasolina…) y que luego se reparte entre quienes corresponda.

| Campo | Para qué sirve |
|-------|----------------|
| **Concepto** | Qué es: «Cena del sábado», «Gasolina». |
| **Quién paga** | El asistente que puso el dinero de su bolsillo. |
| **Categoría** | Restaurante, copas, regalo, supermercado, alojamiento, combustible, sustancias, efectivo u otros. Sirve para el icono y el desglose del informe. |
| **Presupuestado** (opcional) | Lo que pensabais gastar; solo orientativo. |
| **Gastado** (obligatorio) | Lo que costó de verdad. |
| **Pagado** | Cuánto se ha abonado ya de ese apunte. Debe estar completo para poder «cerrar» el apunte y, al final, cerrar la cuenta. |
| **Tique** (foto, opcional) | Una foto del recibo para comprobar importes. Se guarda solo en tu móvil; **no viaja** en la sincronización. |

**Reparto**: abajo eliges cómo se divide ese apunte.

- **A partes iguales (`=`)**: marca quién participa y se divide entre ellos por
  igual.
- **Porcentajes (`%`)**: cada participante tiene un **fader** (barra deslizante).
  Muévelo para darle más o menos peso. El **pin 📌** fija el porcentaje de esa
  persona para que no cambie: al mover otro fader, Bote reparte el resto solo
  entre los que están sueltos. Así puedes decir «Marta paga el 40 % fijo y lo
  demás a partes iguales» sin hacer cuentas.

### Paso 3 — El informe y cerrar la cuenta

- **Informe**: en cualquier momento puedes ver el *informe provisional* con el
  balance (quién ha pagado, cuánto le corresponde, si recupera o debe dinero), el
  desglose **por categoría** con barras, y la sección **quién paga a quién** con
  las transferencias mínimas para saldar todo.
- **Cerrar la cuenta** (candado): cuando ya no habrá más gastos, pulsa *Cerrar la
  cuenta*. Se genera el **informe definitivo** y ya no se pueden añadir apuntes
  (requiere que todos los apuntes tengan su importe pagado). Se puede *Reabrir*
  si hace falta.
- **Ha liquidado**: cada persona marca cuándo hace su pago. Al liquidar tu parte,
  el evento sale de tu lista.
- **Calendario**: botón para añadir la fecha del evento al calendario de Android.

> 🤓 **Dato avanzado — el «modo extorsión» (opcional, apagado de fábrica)**
>
> En Ajustes puedes activar el modo *«plata o plomo»*. Cuando está encendido,
> aparece un botón en el informe que **redacta solo el mensaje de cobro** para
> cada persona que te debe dinero, a partir de una plantilla que tú editas. Puedes
> usar los comodines `{nombre}`, `{importe}`, `{evento}` y `{telefono}` (el tuyo,
> para que hagan el Bizum) y Bote los rellena con los datos reales. No cobra nada
> ni envía nada por su cuenta: solo te deja el texto listo para que lo mandes por
> WhatsApp. Es una broma con función: ahorra escribir «oye, que me debes 12,50 €»
> quince veces.

### Compartir y sincronizar el evento

Para que el grupo tenga la misma cuenta hay que pasar el evento entre móviles.
Bote lo fusiona **apunte a apunte**, así que da igual quién apunte qué en su
teléfono: al juntarlos, no se pierde nada ni se duplica.

- **Por QR o archivo** (sin servidor): *Enviar por QR o archivo*. El otro lo
  recibe escaneando el QR en persona o abriendo el archivo (WhatsApp, email…).
  Al importarlo, Bote le pregunta *quién es* en ese evento.
- **Automática por la nube** (opcional): ver el recuadro y la sección siguiente.

> 🤓 **Dato avanzado — cómo se fusionan dos móviles sin pelearse**
>
> Cada apunte tiene un identificador único y una marca de tiempo. Cuando importas
> un evento, Bote no lo sobrescribe: compara apunte por apunte y **se queda con la
> versión modificada más recientemente**. Las marcas de «ya he pagado» nunca se
> pierden, y si alguien borró un apunte, no «resucita» al sincronizar (se guarda
> una *lápida* que recuerda que estaba borrado). El registro de actividad de cada
> móvil también se une sin repetir líneas. En la práctica: cada uno apunta sus
> cosas cuando quiera, y cuando os sincronizáis, la cuenta queda completa y
> coherente sin que nadie tenga que rehacer nada. Las fotos de los tiques son lo
> único que no viaja (se quedan en cada móvil).

> 🤓 **Dato avanzado — la sincronización automática en la nube (opcional)**
>
> Además del QR y el archivo, Bote puede sincronizarse solo a través de internet,
> pero **el servidor lo pones tú**: no hay una cuenta central de Bote ni nadie que
> vea vuestros datos. Uno del grupo crea gratis un proyecto en
> [Supabase](https://supabase.com) (o usa cualquier PostgREST propio), pega una
> tabla, y copia dos datos (una URL y una clave). A partir de ahí el evento sube y
> baja solo, pero **la fusión sigue siendo local** (la misma lógica del QR); el
> servidor es solo un buzón donde dejar y recoger la copia.
>
> Lo más cómodo: **solo lo configura quien crea el evento**, y esa configuración
> **viaja dentro del evento** al compartirlo. Los demás, al importarlo por QR o
> archivo, quedan conectados **sin teclear nada**. Cada grupo puede tener su propio
> servidor, así que puedes estar en varios grupos a la vez sin líos. Aviso honesto:
> quien tenga la clave puede leer esa tabla, así que compártela solo con tu grupo.
> Los pasos técnicos exactos están más abajo, en *Sincronización automática en la
> nube*.

> 🤓 **Dato avanzado — el detector de pagos desde las notificaciones (opcional)**
>
> En Ajustes → *Detector de pagos* puedes darle a Bote acceso a leer las
> notificaciones del móvil. ¿Para qué? Cuando llega una notificación de tu banco
> que parece una compra (un cargo con importe y comercio), Bote se ofrece a
> **apuntarla como gasto con el importe y el comercio ya rellenados**: tú solo
> eliges en qué evento va y lo confirmas. Es opcional y está **apagado de
> fábrica**. Importante para tu tranquilidad: **todo se procesa dentro de tu
> teléfono**, no se envía nada a ningún servidor; simplemente te ahorra teclear el
> importe de lo que acabas de pagar con tarjeta. Requiere concederle el acceso a
> notificaciones de Android, que puedes revocar cuando quieras.

### Ajustes (resumen)

- **Tema** (blanco / negro / sistema) y **colores** principal y de acento
  (negro y rojo por defecto).
- **Notificaciones**: recordatorio el día del evento, aviso al cerrar una cuenta
  y recordatorio de pagos pendientes.
- **Modo extorsión** y su plantilla de mensaje.
- **Detector de pagos** (acceso a notificaciones).
- **Servidor de sincronización por defecto** (URL y clave para tu grupo habitual).

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

3. *(Opcional)* Para sincronizar también el **avatar del evento** (a baja
   resolución, 256×256 ≈ 20 KB), crea un bucket público `avatares` y deja que
   la clave anon pueda subir:

```sql
insert into storage.buckets (id, name, public)
values ('avatares', 'avatares', true)
on conflict (id) do nothing;

create policy "avatares anon sube" on storage.objects
  for insert to anon with check (bucket_id = 'avatares');
create policy "avatares anon actualiza" on storage.objects
  for update to anon using (bucket_id = 'avatares');
```

   Si no creas el bucket, todo sigue funcionando; simplemente no se sincroniza
   el avatar.

4. Copia la **URL del proyecto** y la **clave `anon`** (Settings → API).

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
clave es la llave del buzón). Solo se sube el **avatar del evento** a baja
resolución (si creaste el bucket `avatares`); los recibos y los PDF adjuntos
siguen siendo locales y nunca salen del dispositivo.

## Compilación

Proyecto Android estándar (Kotlin, Material Components, Room). Requiere
JDK 17 y el SDK de Android (compileSdk 34).

Hay **dos sabores** (mismo código): `foss` (F-Droid, todo gratis, sin
librerías de Google) y `play` (freemium, la sync se desbloquea por
suscripción vía Play Billing). El APK que va a F-Droid es el `foss`:

```
gradle assembleFossDebug      # depuración
gradle assembleFossRelease    # firmado (el de F-Droid)
gradle assemblePlayRelease    # variante de Google Play
```

El cifrado extremo a extremo (frase en Ajustes) es común a ambos sabores.

En GitHub Actions hay dos flujos: `build.yml` compila el APK de depuración en
cada push a `main`, y `release.yml` compila el APK firmado al etiquetar
`v*`, lo publica en el repositorio personal de F-Droid
(https://tizzenn.github.io/fdroid/repo) y crea la release de GitHub.

Secrets necesarios para publicar: `BOTE_KEYSTORE_B64`, `BOTE_KEYSTORE_PASS`,
`FDROID_DEPLOY_KEY`, `FDROID_KEYSTORE_B64` y `FDROID_KEYSTORE_PASS`.

## Licencia

GNU GPL v3: cualquier redistribución o derivado debe publicar su código
fuente bajo la misma licencia. Ver [LICENSE](LICENSE).
