# libs/

Put these jars here before building (they are compile-only and are **not** part of this repository):

| Jar | Source |
|---|---|
| `create-1.21.1-6.0.10.jar` | [Create](https://modrinth.com/mod/create) |
| `flywheel-neoforge-1.21.1-1.0.6.jar` | bundled with Create (or [Flywheel](https://modrinth.com/mod/flywheel)) |
| `ponder-neoforge-1.0.82+mc1.21.1.jar` | bundled with Create (Ponder / Catnip) |
| `Registrate-MC1.21-1.3.0+67.jar` | bundled with Create (Registrate) |
| `sable-neoforge-1.21.1-2.0.6.jar` | Sable |
| `sable-companion-common-1.21.1-1.6.0.jar` | Sable companion library |

Any jar in this folder is added to the compile classpath. Newer patch versions usually work.
For the game tests, also put the Create and Sable jars into `run/mods/`.
