---
paths:
  - "pom.xml"
---

# pom.xml

- **jackson must stay declared before EyesUtilities** — that jar embeds a partial,
  unrelocated jackson-core 2.16.1 fragment; whichever is first on the classpath wins,
  and the fragment winning means `NoSuchMethodError` at runtime. Respect the guarding
  pom comment when adding or reordering dependencies.
- **`libs/` is a checked-in file-based Maven repo** for EyesUtilities (not published
  anywhere public) — never remove it or its jars. Upgrade procedure:
  DEVELOPING.md §Landmines.
- **`-Dowasp.skip=true` is a historical no-op** — the plugin is gone; don't hunt for
  it, and don't strip the flag from CI/installer commands as a "cleanup".
- **`dependency-reduced-pom.xml` is generated** by the shade plugin and gitignored —
  ignore it.
- **Release lockstep** — the `<version>` must match the release tag's base version;
  bump pom + CHANGELOG.md before tagging (see RELEASING.md).
