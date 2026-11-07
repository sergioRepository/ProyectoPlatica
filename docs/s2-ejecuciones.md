# Ejecuciones reales del agente (S2)

Exportadas el 2026-11-07 de la tabla `agent_events` en PostgreSQL de producción (Neon), con una conexión de solo lectura. Las horas están en America/Bogota.

En la base hay **34 ejecuciones de 5 teléfonos**, desde el 2026-10-23 hasta el 2026-11-07:

| Resultado | Ejecuciones |
| --- | --- |
| Intervino y el teléfono confirmó el bloqueo (`executed`) | 11 |
| Quiso intervenir y la política lo rechazó por el tope diario (`rejected`, `daily_cap`) | 11 |
| Decidió no intervenir (`no_action`) | 7 |
| Evidencia insuficiente (`insufficient_evidence`) | 5 |

21 ejecuciones fueron periódicas, lanzadas solas por la app, y 13 manuales, pedidas por el usuario desde la app.
Ninguna terminó en `failed` ni quedó sin confirmar.

**Selección.** Las 10 de abajo se eligieron a mano para cubrir los cinco teléfonos, los dos disparadores y los cuatro resultados. No son las 10 más recientes: esas son casi todas del Teléfono A.
Los teléfonos aparecen como A–E; no se publica `device_id`, `request_id` ni el id del evento.

**Herramientas.** Cada paso es una herramienta del agente:
- **evidencia:** `consultar_evidencia_uso`
- **historial:** `consultar_historial_intervenciones`
- **intervención:** `ejecutar_intervencion`

El estado entre paréntesis es el que devolvió cada herramienta. El uso es el de la ventana que pidió el agente, de 15 min a 24 h; en estas filas suele ser la última media hora o la última hora.

| # | Fecha | Teléfono | Disparador | Herramientas | Uso por app | Decisión | Política | Estado | Qué pasó |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 2026-11-07 15:41 | A | periódico | evidencia (ok) → historial (ok) → intervención (sent) | Instagram 29,3 min, 3 aperturas | `intervene` | - | `executed` | Casi media hora de Instagram en horario de trabajo y sin enfoque activo: el agente propuso 15 min de enfoque sobre Instagram y el teléfono lo aplicó al instante. |
| 2 | 2026-11-06 06:51 | A | periódico | evidencia (ok) → historial (ok) → intervención (rejected) | Instagram 57,7 min, 6 aperturas | `intervene` | `daily_cap` | `rejected` | El agente quiso otra sesión de 15 min por casi una hora de Instagram, pero la política la bloqueó porque ese teléfono ya había llegado al tope diario de intervenciones. |
| 3 | 2026-11-05 22:15 | A | periódico | evidencia (ok) → historial (ok) | Instagram 43,1 min, 5 aperturas | `no_action` | - | - | Aunque el uso era alto, el historial mostró una intervención hace unos 30 min y varios rechazos por el tope, así que el agente decidió no insistir. |
| 4 | 2026-11-05 21:14 | A | periódico | evidencia (ok) → historial (ok) → intervención (sent) | Instagram 31,1 min, 10 aperturas | `intervene` | - | `executed` | Diez aperturas de Instagram en una hora de trabajo: se aplicó un enfoque de 15 min y el teléfono lo confirmó. |
| 5 | 2026-11-03 11:48 | B | manual | evidencia (ok) → evidencia (ok) → historial (no_data) | WhatsApp 0,8 min, 10 aperturas; One UI Home 0,6 min, 4; Nu 0,3 min, 1; Chrome 0,1 min, 1; GitHub 0,0 min, 1 | `insufficient_evidence` | - | - | El agente pidió la evidencia dos veces, pero solo encontró aperturas cortas y dispersas, sin uso prolongado de apps bloqueadas, y no actuó. |
| 6 | 2026-10-24 23:09 | C | manual | evidencia (ok) → historial (no_data) → intervención (sent) | Instagram 39,9 min, 6 aperturas; TikTok 5,4 min, 3 | `intervene` | - | `executed` | Primer análisis de ese teléfono, sin historial: 40 min de Instagram de noche llevaron a un enfoque de 15 min sobre Instagram y TikTok, y el teléfono lo aplicó. |
| 7 | 2026-10-24 15:06 | D | manual | evidencia (ok) → historial (ok) | TikTok 3,4 min, 5 aperturas; Instagram 0,0 min, 5 | `no_action` | - | - | Uso bajo y una sesión de enfoque ya en curso: el agente no agregó otra. |
| 8 | 2026-10-24 15:00 | D | manual | evidencia (ok) → historial (ok) → intervención (sent) | TikTok 6,4 min, 4 aperturas; Instagram 0,0 min, 2 | `intervene` | - | `executed` | Poco tiempo de uso, pero muchas aperturas seguidas de TikTok e Instagram en medio de una tarea: el agente propuso 10 min de enfoque y el teléfono lo aplicó. |
| 9 | 2026-10-24 06:38 | D | manual | evidencia (ok) → historial (ok) | TikTok 3,6 min, 3 aperturas | `insufficient_evidence` | - | - | Menos de 4 min de TikTok y sin aperturas repetidas: no había señal de distracción para justificar un bloqueo. |
| 10 | 2026-10-23 22:29 | E | manual | evidencia (ok) → historial (ok) | TikTok 1,0 min, 1 apertura | `no_action` | - | - | Un minuto de TikTok en media hora: el agente concluyó que no hacía falta intervenir. |

## Cómo leer los estados

- **`executed`:** el backend envió la orden y el teléfono confirmó que aplicó el bloqueo, en todos los casos de la base en menos de un segundo.
- **`rejected` con `daily_cap`:** el modelo pidió intervenir, pero la política determinista del backend lo frenó antes de llegar al teléfono. Así se ve que el modelo no decide solo.
- **Sin estado:** el agente no propuso acción, ya fuera por `no_action` o por `insufficient_evidence`, así que no se llamó a `ejecutar_intervencion`.

`observed_outcome`, el uso medido después de intervenir, está vacío en todas las filas, como dice el [reporte técnico](reporte-tecnico-hito-2.md).
