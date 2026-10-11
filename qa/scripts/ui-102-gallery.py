"""Build a local, searchable visual-review gallery from emulator captures.

The script only reads PNG captures and writes contact sheets plus gallery.html.
It never re-captures, renames, edits, or deletes source screenshots.
"""

from __future__ import annotations

import html
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1] / "current" / "1.0.2"
STAGES = ("before", "after")
CATEGORIES = ("书架", "导入", "阅读器", "设置", "备份统计", "系统")
GROUP_BASE_ALIASES = {
    "13-import-empty": "13-import",
    "21-reader-full-settings-themes": "21-reader-full-settings",
    "34-touch-lock": "34-reader-touch-lock",
    "42-layout-menu": "42-library-layout-menu",
    "42-library-layout": "42-library-layout-menu",
    "44-library-sort-options": "44-library-options",
    "49-collection-picker": "49-selection-collections",
    "49-collection-picker-dialog": "49-selection-collections",
    "57-restore-preview-current": "57-restore-preview",
    "65-theme-time-cancelled": "65-theme-schedule-fixed",
    "69-library-delete-book": "69-book-delete",
    "73-collection-delete": "74-collection-delete",
    "74-collection-filtered": "71-collection-filter",
}

# Stable primary choices for captures whose filenames differ from their group.
# Prefer the canonical filename when present, then these explicit aliases.
PRIMARY_CAPTURE_ALIASES = {
    "34-reader-touch-lock": ("34-touch-lock",),
    "42-library-layout-menu": ("42-layout-menu", "42-library-layout"),
    "44-library-options": ("44-library-sort-options",),
    "49-selection-collections": ("49-collection-picker-dialog", "49-collection-picker"),
    "69-book-delete": ("69-library-delete-book",),
    "74-collection-delete": ("73-collection-delete",),
}
FEATURED_STATES = {
    "21-reader-full-settings": ("21-reader-full-settings-themes",),
}
HISTORICAL_CAPTURES = {
    ("after", "21-reader-full-settings-themes-2"):
        "较早版本（2026-09-29 21:23）；描边保存按钮已在最终版移除",
}

# The numeric prefix is stable in the capture script. The title mapping keeps
# the original English filenames intact while making the gallery readable.
PAGE_META = {
    "01-library-empty": ("书架 · 空状态", "书架"),
    "02-settings": ("设置首页", "设置"),
    "03-statistics-empty": ("统计 · 空状态", "备份统计"),
    "04-global-appearance": ("全局阅读外观", "设置"),
    "05-reading-behavior": ("阅读行为", "设置"),
    "06-theme-schedule": ("主题与自动切换", "设置"),
    "07-fonts": ("导入字体", "设置"),
    "08-advanced-typography": ("精细排版与专注带", "设置"),
    "09-reading-info": ("阅读信息", "设置"),
    "10-backup": ("备份与恢复", "备份统计"),
    "11-backup-export-options": ("备份导出选项", "备份统计"),
    "12-annotation-export": ("批注导出", "备份统计"),
    "13-import": ("导入 · 选择文件", "导入"),
    "14-import-encoding": ("导入 · 编码确认", "导入"),
    "15-system-downloads": ("系统文件 · 下载目录", "系统"),
    "15-system-file-picker": ("系统文件选择器", "系统"),
    "15-system-roots": ("系统文件 · 根目录", "系统"),
    "16-import-success": ("导入 · 成功结果", "导入"),
    "17-book-edit": ("书籍信息编辑", "书架"),
    "18-reading": ("阅读页", "阅读器"),
    "19-reader-menu": ("阅读 · 顶部菜单", "阅读器"),
    "20-reader-quick-settings": ("阅读 · 快速设置", "阅读器"),
    "21-reader-full-settings": ("阅读 · 完整设置", "阅读器"),
    "22-reader-advanced": ("阅读 · 更多设置", "阅读器"),
    "23-directory": ("目录", "阅读器"),
    "24-bookmarks-empty": ("书签 · 空状态", "阅读器"),
    "25-reader-search": ("正文搜索", "阅读器"),
    "26-notes-empty": ("笔记 · 空状态", "阅读器"),
    "27-reader-notes-export": ("笔记导出", "阅读器"),
    "28-reader-more": ("阅读 · 更多操作", "阅读器"),
    "29-reader-progress": ("阅读进度定位", "阅读器"),
    "30-chapter-management": ("目录管理", "阅读器"),
    "31-chapter-rename": ("目录 · 重命名章节", "阅读器"),
    "32-chapter-delete": ("目录 · 删除章节", "阅读器"),
    "33-chapter-reanalyze": ("目录 · 重新分析", "阅读器"),
    "34-reader-touch-lock": ("触摸锁定", "阅读器"),
    "35-text-selection-actions": ("正文选择 · 操作菜单", "阅读器"),
    "35-text-selection": ("正文选择", "阅读器"),
    "36-note-editor-filled": ("笔记编辑 · 已填写", "阅读器"),
    "36-note-editor": ("笔记编辑", "阅读器"),
    "37-notes": ("笔记列表", "阅读器"),
    "38-bookmarks": ("书签列表", "阅读器"),
    "39-bookmark-delete": ("删除书签", "阅读器"),
    "40-library-grid": ("书架 · 网格", "书架"),
    "41-import-batch-duplicate": ("批量导入 · 重复处理", "导入"),
    "41-import-batch-end": ("批量导入 · 完成", "导入"),
    "42-library-layout-menu": ("书架 · 布局菜单", "书架"),
    "43-library-list": ("书架 · 列表", "书架"),
    "44-library-options": ("书架 · 视图与排序", "书架"),
    "45-book-menu": ("书籍操作菜单", "书架"),
    "46-book-edit": ("书籍编辑", "书架"),
    "47-collection-create": ("新建集合", "书架"),
    "48-library-selection": ("书架 · 批量选择", "书架"),
    "49-selection-collections": ("批量选择 · 集合操作", "书架"),
    "50-library-filters": ("书架 · 筛选", "书架"),
    "51-statistics-populated": ("统计 · 有效数据", "备份统计"),
    "52-statistics-book": ("统计 · 单书", "备份统计"),
    "53-statistics-clear": ("统计 · 清除确认", "备份统计"),
    "54-system-backup-save": ("系统文件 · 备份保存", "系统"),
    "55-backup-result": ("备份 · 结果", "备份统计"),
    "56-system-backup-open": ("系统文件 · 打开备份", "系统"),
    "57-restore-preview": ("恢复 · 预览", "备份统计"),
    "58-restore-confirm": ("恢复 · 确认", "备份统计"),
    "59-handoff-export": ("单书接力 · 导出", "备份统计"),
    "60-handoff-result": ("单书接力 · 结果", "备份统计"),
    "61-handoff-preview": ("单书接力 · 预览", "备份统计"),
    "62-theme-copy": ("主题 · 复制", "设置"),
    "63-theme-rename": ("主题 · 重命名", "设置"),
    "64-theme-delete": ("主题 · 删除", "设置"),
    "65-theme-schedule-fixed": ("主题计划 · 定时", "设置"),
    "66-reader-quick-dark": ("阅读 · 深色快速设置", "阅读器"),
    "67-library-search": ("书架 · 搜索", "书架"),
    "68-library-search-empty": ("书架 · 搜索空结果", "书架"),
    "69-book-delete": ("删除书籍", "书架"),
    "70-collection-view": ("集合视图", "书架"),
    "71-collection-filter": ("集合筛选", "书架"),
    "72-collection-menu": ("集合菜单", "书架"),
    "73-collection-rename": ("重命名集合", "书架"),
    "74-collection-delete": ("删除集合", "书架"),
    "75-import-return": ("导入 · 返回结果", "导入"),
    "76-quick-preview": ("阅读 · 快速设置预览", "阅读器"),
    "77-reader-menu-dark": ("阅读 · 深色菜单", "阅读器"),
    "78-auto-page": ("阅读 · 自动翻页设置", "阅读器"),
    "79-sleep-timer": ("阅读 · 定时停止设置", "阅读器"),
    "80-auto-page-running": ("阅读 · 自动翻页运行中", "阅读器"),
    "81-sleep-expired": ("阅读 · 定时停止到期", "阅读器"),
    "60-handoff-save": ("系统文件 · 接力包选择", "系统"),
    "66-reading-dark": ("阅读 · 深色正文", "阅读器"),
    "77-reader-dark-menu": ("阅读 · 深色菜单", "阅读器"),
    "78-reader-auto-page": ("阅读 · 自动翻页设置", "阅读器"),
    "79-reader-stop-timer": ("阅读 · 定时停止设置", "阅读器"),
    "80-auto-background-resume": ("阅读 · 自动翻页 · 返回前台", "阅读器"),
    "80-auto-background-status": ("阅读 · 自动翻页 · 后台状态", "阅读器"),
    "80-auto-background-still": ("阅读 · 自动翻页 · 后台未继续", "阅读器"),
    "80-auto-page-after-background": ("阅读 · 自动翻页 · 后台返回后", "阅读器"),
    "80-auto-page-background-paused": ("阅读 · 自动翻页 · 后台暂停", "阅读器"),
    "80-auto-page-before-background": ("阅读 · 自动翻页 · 切后台前", "阅读器"),
    "80-auto-page-first": ("阅读 · 自动翻页 · 第一次翻页", "阅读器"),
    "80-auto-page-paused": ("阅读 · 自动翻页 · 已暂停", "阅读器"),
    "80-auto-page-second": ("阅读 · 自动翻页 · 第二次翻页", "阅读器"),
    "80-auto-page-start": ("阅读 · 自动翻页 · 开始", "阅读器"),
    "81-timer-expired-reader": ("阅读 · 定时停止到期 · 阅读页", "阅读器"),
    "81-timer-expired-status": ("阅读 · 定时停止到期 · 状态", "阅读器"),
    "81-timer-expired": ("阅读 · 定时停止到期 · 批注面板", "阅读器"),
    "82-chapter-actions": ("目录 · 章节操作", "阅读器"),
    "83-progress-preview": ("阅读 · 进度跳转预览", "阅读器"),
    "85-theme-time-invalid": ("主题 · 时间输入校验", "设置"),
    "85-theme-time-picker": ("主题 · 时间输入", "设置"),
    "86-theme-actions": ("主题 · 操作菜单", "设置"),
    "87-restore-result": ("恢复 · 完成结果", "备份统计"),
    "88-import-encoding-preview": ("导入 · 编码预览", "导入"),
    "89-page-jump-confirmed": ("阅读 · 跳转位置确认", "阅读器"),
    "89-page-jump-directory": ("阅读 · 跳转位置 · 目录选章", "阅读器"),
    "89-page-jump-long": ("阅读 · 跳转后长文定位", "阅读器"),
    "89-page-jump-next": ("阅读 · 跳转后下一章", "阅读器"),
    "89-page-jump-reopened": ("阅读 · 重开后的跳转位置", "阅读器"),
    "89-page-jump-restored": ("阅读 · 重启后跳转位置", "阅读器"),
    "89-page-jump-return": ("阅读 · 返回跳转前位置", "阅读器"),
    "90-reader-tap-grid": ("阅读 · 九宫格点击设置", "阅读器"),
    "91-appearance-large-text": ("全局阅读外观 · 大字号", "设置"),
    "91-behavior-large-text": ("全局阅读行为 · 大字号", "设置"),
    "91-settings-discard": ("设置 · 放弃未保存更改", "设置"),
    "91-tap-grid-action": ("全局阅读行为 · 九宫格动作", "设置"),
    "91-tap-grid-large-text": ("全局阅读行为 · 九宫格大字号", "设置"),
}


def load_font(size: int):
    """Prefer a Windows font with Chinese glyphs for contact-sheet labels."""

    candidates = (
        Path("C:/Windows/Fonts/msyh.ttc"),
        Path("C:/Windows/Fonts/simhei.ttf"),
        Path("C:/Windows/Fonts/arial.ttf"),
    )
    for candidate in candidates:
        if candidate.exists():
            try:
                return ImageFont.truetype(str(candidate), size)
            except OSError:
                continue
    return ImageFont.load_default()


FONT = load_font(16)


def sort_key(stem: str):
    match = re.match(r"(\d+)-", stem)
    return (int(match.group(1)) if match else 999, stem)


def screenshot_files(stage: str):
    stage_root = ROOT / stage
    if not stage_root.exists():
        return []
    return sorted(
        (path for path in stage_root.glob("*.png") if path.stem != "current"),
        key=lambda path: sort_key(path.stem),
    )


def group_base(stem: str) -> str:
    """Collapse numbered/top captures into the page's primary screenshot."""

    collapsed = re.sub(r"-(?:\d+|top)$", "", stem)
    return GROUP_BASE_ALIASES.get(collapsed, collapsed)


def page_meta(stem: str):
    base = group_base(stem)
    if base in PAGE_META:
        return PAGE_META[base]
    number = stem.split("-", 1)[0]
    fallback_category = {
        "01": "书架",
        "02": "设置",
        "03": "备份统计",
        "04": "设置",
        "05": "设置",
        "06": "设置",
        "07": "设置",
        "08": "设置",
        "09": "设置",
        "10": "备份统计",
        "11": "备份统计",
        "12": "备份统计",
        "13": "导入",
        "14": "导入",
        "15": "系统",
    }.get(number, "阅读器")
    return (f"页面 {number}", fallback_category)


def variant_label(stem: str) -> str:
    if stem in ("21-reader-full-settings-themes", "21-reader-full-settings-themes-2"):
        return "阅读 · 完整设置 · 主题"
    base = group_base(stem)
    suffix = stem[len(base):].lstrip("-")
    return f"{page_meta(base)[0]} · 额外状态 {suffix or '1'}"


def relative_capture(stage: str, stem: str) -> str:
    return (Path(stage) / f"{stem}.png").as_posix()


def contact_sheets():
    """Keep the original contact-sheet output, with Chinese labels."""

    for stage in STAGES:
        files = screenshot_files(stage)
        for batch in range(0, len(files), 9):
            selected = files[batch:batch + 9]
            canvas = Image.new(
                "RGB",
                (900, ((len(selected) + 2) // 3) * 704),
                "#e8e4dc",
            )
            draw = ImageDraw.Draw(canvas)
            for index, file in enumerate(selected):
                image = Image.open(file).convert("RGB")
                image.thumbnail((294, 654))
                x, y = (index % 3) * 300, (index // 3) * 704
                canvas.paste(image, (x + (300 - image.width) // 2, y + 34))
                title, _ = page_meta(file.stem)
                if (stage, file.stem) in HISTORICAL_CAPTURES:
                    title = f"历史版本 · {title}"
                draw.text((x + 5, y + 7), title[:28], font=FONT, fill="#27241e")
            canvas.save(ROOT / f"{stage}-contact-{batch // 9 + 1:02}.jpg", quality=88)


def shot_markup(stage: str, stem: str, *, main: bool) -> str:
    path = ROOT / stage / f"{stem}.png"
    stage_label = "调整前" if stage == "before" else "调整后"
    if not path.exists():
        missing = f"{stage_label} · 此状态未单独采集"
        return f'<div class="shot missing {stage}"><small>{html.escape(missing)}</small></div>'
    rel = relative_capture(stage, stem)
    title = page_meta(stem)[0] if main else variant_label(stem)
    classes = "shot main-shot" if main else "shot extra-shot"
    history_note = HISTORICAL_CAPTURES.get((stage, stem))
    if history_note:
        stage_label = "历史截图 · 不代表当前界面"
        classes += " historical-shot"
    elif stage == "after" and stem == "21-reader-full-settings-themes":
        stage_label = "调整后 · 最终主题页面"
    alt = html.escape(f"{stage_label} · {title}", quote=True)
    note_markup = (
        f'<p class="muted">{html.escape(history_note)}</p>' if history_note else ""
    )
    return (
        f'<figure class="{classes} {stage}">'
        f'<figcaption><small>{html.escape(stage_label)} · {html.escape(title)}</small></figcaption>'
        f'<a href="{html.escape(rel, quote=True)}">'
        f'<img loading="lazy" src="{html.escape(rel, quote=True)}" alt="{alt}"></a>'
        f'<code>{html.escape(stem)}.png</code>{note_markup}</figure>'
    )


def build_groups():
    all_files = {stage: screenshot_files(stage) for stage in STAGES}
    all_stems = {path.stem for paths in all_files.values() for path in paths}
    groups = {}
    for stem in all_stems:
        base = group_base(stem)
        groups.setdefault(base, {"main": base, "extras": set()})
        if stem != base:
            groups[base]["extras"].add(stem)
    return all_files, dict(sorted(groups.items(), key=lambda item: sort_key(item[0])))


def current_markup() -> str:
    current = []
    for stage in STAGES:
        path = ROOT / stage / "current.png"
        if path.exists():
            rel = relative_capture(stage, "current")
            stage_label = "调整前当前状态" if stage == "before" else "调整后当前状态"
            current.append(
                f'<figure class="shot current-shot {stage}">'
                f'<figcaption><small>{html.escape(stage_label)}</small></figcaption>'
                f'<a href="{html.escape(rel, quote=True)}">'
                f'<img loading="lazy" src="{html.escape(rel, quote=True)}" '
                f'alt="{html.escape(stage_label, quote=True)}"></a>'
                f'<code>current.png</code></figure>'
            )
    if not current:
        return ""
    return (
        '<section class="current-section" id="current-capture">'
        '<h2>当前环境概览</h2>'
        '<p class="muted">此截图不参与 before/after 配对，仅作为采集环境参考。</p>'
        f'<div class="shot-grid">{"".join(current)}</div></section>'
    )


def primary_capture(stage: str, base: str, group: dict) -> str:
    candidates = (base, *PRIMARY_CAPTURE_ALIASES.get(base, ()),
                  *sorted(group["extras"], key=sort_key))
    for stem in candidates:
        if (stage, stem) not in HISTORICAL_CAPTURES and (ROOT / stage / f"{stem}.png").exists():
            return stem
    return base


def article_markup(base: str, group: dict) -> str:
    title, category = page_meta(base)
    extras = sorted(group["extras"], key=sort_key)
    primary = {stage: primary_capture(stage, base, group) for stage in STAGES}
    before_exists = (ROOT / "before" / f"{primary['before']}.png").exists()
    after_exists = (ROOT / "after" / f"{primary['after']}.png").exists()
    shots = "".join(shot_markup(stage, primary[stage], main=True) for stage in STAGES)
    displayed = {(stage, primary[stage]) for stage in STAGES}
    featured_shots = []
    for stem in FEATURED_STATES.get(base, ()):
        for stage in STAGES:
            if (stage, stem) not in displayed and (ROOT / stage / f"{stem}.png").exists():
                featured_shots.append(shot_markup(stage, stem, main=False))
                displayed.add((stage, stem))
    featured_markup = (
        f'<div class="shot-grid">{"".join(featured_shots)}</div>' if featured_shots else ""
    )
    extra_markup = ""
    extra_shots, historical_shots = [], []
    for extra in extras:
        for stage in STAGES:
            if (stage, extra) in displayed or not (ROOT / stage / f"{extra}.png").exists():
                continue
            target = historical_shots if (stage, extra) in HISTORICAL_CAPTURES else extra_shots
            target.append(shot_markup(stage, extra, main=False))
    if extra_shots:
        extra_markup = (
            f'<details class="extra-states"><summary>展开其他状态截图 '
            f'（{len(extra_shots)} 张）</summary><div class="shot-grid extra-grid">'
            f'{"".join(extra_shots)}</div></details>'
        )
    if historical_shots:
        extra_markup += (
            f'<details class="extra-states historical-states"><summary>查看较早版本 '
            f'（{len(historical_shots)} 张，不代表当前界面）</summary>'
            f'<div class="shot-grid extra-grid">{"".join(historical_shots)}</div></details>'
        )
    file_hint = f"{base}.png"
    searchable = html.escape(
        f"{title} {category} {base} {' '.join(extras)}".lower(),
        quote=True,
    )
    after_badge = "after" if after_exists else "after · 未采集"
    after_class = "after-badge" if after_exists else "missing-badge"
    return (
        f'<article class="page-card" data-title="{html.escape(title, quote=True)}" '
        f'data-category="{html.escape(category, quote=True)}" '
        f'data-search="{searchable}" data-before="{str(before_exists).lower()}" '
        f'data-after="{str(after_exists).lower()}">'
        f'<div class="page-heading"><div><span class="category">{html.escape(category)}</span>'
        f'<h2>{html.escape(title)}</h2><code>{html.escape(file_hint)}</code></div>'
        f'<div class="availability"><span class="badge before-badge">before</span>'
        f'<span class="badge {after_class}">{after_badge}</span></div></div>'
        f'<div class="shot-grid">{shots}</div>{featured_markup}{extra_markup}</article>'
    )


def gallery_html() -> str:
    _, groups = build_groups()
    cards = "".join(article_markup(base, group) for base, group in groups.items())
    category_options = "".join(
        f'<option value="{html.escape(category, quote=True)}">{html.escape(category)}</option>'
        for category in CATEGORIES
    )
    total = len(groups)
    current = current_markup()
    return f'''<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width">
<title>新阅 1.0.2 页面审查</title>
<style>
:root {{ color-scheme: light; --paper:#f4f1ea; --surface:#fffdf8; --line:#d9d2c5; --ink:#282b28; --muted:#6e756e; --accent:#365d53; --missing:#8b5048; }}
* {{ box-sizing:border-box; }}
body {{ background:var(--paper); color:var(--ink); font:15px system-ui,-apple-system,"Microsoft YaHei",sans-serif; margin:0 auto; max-width:1240px; padding:30px 22px 72px; }}
h1,h2 {{ font-family:serif; }}
h1 {{ margin:0 0 10px; font-size:clamp(28px,4vw,42px); }}
h2 {{ margin:5px 0 4px; font-size:18px; }}
p {{ line-height:1.7; }}
.intro {{ background:var(--surface); border:1px solid var(--line); border-radius:12px; padding:18px 20px; }}
.intro strong {{ color:var(--accent); }}
.controls {{ position:sticky; top:0; z-index:5; display:flex; flex-wrap:wrap; gap:10px; align-items:center; background:var(--paper); padding:14px 0; }}
.controls input,.controls select {{ min-height:42px; border:1px solid var(--line); border-radius:8px; background:var(--surface); color:var(--ink); padding:8px 12px; font:inherit; }}
.controls input {{ flex:1 1 280px; }}
.controls select {{ flex:0 1 170px; }}
.count {{ color:var(--muted); margin-left:auto; }}
.current-section {{ border:1px solid var(--line); background:var(--surface); border-radius:12px; padding:18px; margin:8px 0 24px; }}
.page-card {{ border-top:1px solid var(--line); padding:22px 0 26px; }}
.page-card.hidden {{ display:none; }}
.page-heading {{ display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }}
.page-heading code,.shot code {{ color:var(--muted); font:12px ui-monospace,SFMono-Regular,Consolas,monospace; overflow-wrap:anywhere; }}
.category {{ color:var(--accent); font-size:12px; letter-spacing:.04em; }}
.availability {{ display:flex; gap:6px; flex-wrap:wrap; justify-content:flex-end; }}
.badge {{ border-radius:99px; padding:4px 8px; background:#e4ece5; color:var(--accent); font-size:12px; }}
.missing-badge {{ background:#f4e1dd; color:var(--missing); }}
.shot-grid {{ display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:18px; margin-top:14px; }}
.shot {{ min-width:0; margin:0; }}
.shot img {{ max-width:100%; max-height:760px; display:block; margin:9px 0; border:1px solid var(--line); border-radius:6px; background:#e8e4dc; }}
.shot a {{ color:inherit; }}
.shot small {{ color:var(--muted); }}
.shot.missing {{ min-height:150px; border:1px dashed #d5aaa3; border-radius:8px; padding:15px; background:#fff8f5; }}
.extra-states {{ margin-top:16px; border-top:1px solid var(--line); padding-top:10px; }}
.extra-states summary {{ cursor:pointer; color:var(--accent); font-weight:600; padding:8px 0; }}
.extra-grid {{ margin-top:8px; }}
.muted {{ color:var(--muted); }}
@media (max-width:760px) {{ body {{ padding:22px 14px 56px; }} .shot-grid {{ grid-template-columns:1fr; }} .count {{ width:100%; margin-left:0; }} .page-heading {{ display:block; }} .availability {{ justify-content:flex-start; margin-top:8px; }} }}
</style>
</head>
<body>
<header class="intro">
<h1>新阅 1.0.2 · 全页审查</h1>
<p><strong>真实模拟器截图：</strong>本页来自 API 37 / 16 KB 模拟器上的真实应用窗口，使用独立测试用户和公开原创样例。系统文件选择器单独标记；截图数量不等于自动测试项。</p>
<p class="muted">默认展示每个页面的主要 before/after 截图；不同文件名按页面映射配对，原始文件名显示在图下。其他状态收在可展开区域；较早版本单独标为历史，不代表当前界面。after 尚未采集的页面会明确显示“after · 未采集”，不会用 before 冒充调整后结果。点击截图可查看原图，页面没有服务或外部依赖。</p>
</header>
<div class="controls" aria-label="截图筛选">
  <input id="search" type="search" placeholder="搜索中文标题、分类或文件名" autocomplete="off">
  <select id="category" aria-label="按分类筛选"><option value="">全部分类</option>{category_options}</select>
  <span id="count" class="count">显示 {total} / {total} 页</span>
</div>
{current}
<main id="pages">{cards}</main>
<script>
const cards = [...document.querySelectorAll('.page-card')];
const search = document.getElementById('search');
const category = document.getElementById('category');
const count = document.getElementById('count');
function filterPages() {{
  const query = search.value.trim().toLowerCase();
  const selected = category.value;
  let visible = 0;
  for (const card of cards) {{
    const matchesQuery = !query || card.dataset.search.includes(query);
    const matchesCategory = !selected || card.dataset.category === selected;
    const show = matchesQuery && matchesCategory;
    card.classList.toggle('hidden', !show);
    if (show) visible += 1;
  }}
  count.textContent = `显示 ${{visible}} / ${{cards.length}} 页`;
}}
search.addEventListener('input', filterPages);
category.addEventListener('change', filterPages);
</script>
</body>
</html>
'''


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    contact_sheets()
    (ROOT / "gallery.html").write_text(gallery_html(), encoding="utf-8")
    _, groups = build_groups()
    print(f"Gallery updated: {len(groups)} page groups; source screenshots unchanged")


if __name__ == "__main__":
    main()
