PAYMENT TRACKER (Fabric, Minecraft 1.21.4, client-side)

Build:  gradle wrapper && ./gradlew build   (needs JDK 21)
Jar:    build/libs/paytracker-1.0.0.jar -> put in .minecraft/mods with Fabric API

Use:    press Right Shift (rebindable in Controls) to open the small panel.
        - Min / Max: type amounts (25m, 1.5b, 25000000), hit Set or Enter.
          Leave Max empty for no maximum.
        - Drag the panel by its purple header to move it (right-click the header to reset).
        - Log shows player, amount, live "time ago". Click the x on a row to delete it.
        - "clear" in the header wipes the whole log (click twice to confirm).

Chat format: edit "patterns" in .minecraft/config/paytracker.json if your
server words payments differently. Each regex needs a named group "amount",
and optionally "suffix" (k/m/b/t) and "player".
