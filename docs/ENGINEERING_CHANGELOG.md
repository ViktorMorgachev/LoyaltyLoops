# ENGINEERING_CHANGELOG.md

Журнал внедрённых изменений. Новая запись добавляется в начало при каждом шипе.

Формат:

```markdown
## {Дата} — {Название}

### Внедрено
- Что реализовано
- Ключевые технические решения
- Краткий список изменённых файлов
```

---

## 2026-07-07 — Стандарт ведения проекта

### Внедрено.
- Добавлен `.dockerignore` (раньше в build context уходили `.git`, `node_modules`, `iosApp/Pods`).
- Введён стандарт ведения проекта: `CLAUDE.md` + скиллы `loyalty-dev` / `loyalty-review` / `loyalty-docs` / `loyalty-design` (перенесены из Selvor/VoyagePay и адаптированы).
- Реорганизована документация: `docs/flows|operations|testing|tasks|reviews|archive|screens`, созданы `TECH_DEBT.md` (18 записей по итогам аудита), `TECH_STACK.md`, `ENGINEERING_NOTES.md`, `SCREEN_DOCUMENTATION_GUIDE.md`.
- Изменённые файлы: `.dockerignore`, `.gitignore`, `README.md`, `CLAUDE.md`, `.claude/skills/*`, `docs/**`.
