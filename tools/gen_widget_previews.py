# Run by gen_widget_layouts.py after the layouts are written: the pictures the launcher shows
# in its widget list. Each is the real layout filled with a sample plan, so it looks like the
# widget will. Uses WRITTEN, write and NL from that script.
import re


def preview(xml, changes):
    """Sets, replaces or (with None) removes attributes of the views whose ids are in changes."""
    lines = xml.split(NL)
    out, i = [], 0
    while i < len(lines):
        m = re.search(r'android:id="@\+id/(\w+)"', lines[i])
        if not m or m.group(1) not in changes:
            out.append(lines[i])
            i += 1
            continue
        indent = re.match(r"\s*", lines[i]).group(0)
        attrs, close = [], None
        while close is None:
            s = lines[i].strip()
            for end in (" />", ">"):
                if s.endswith(end) and s[: -len(end)].endswith('"'):
                    s, close = s[: -len(end)], end
                    break
            name, value = s.split("=", 1)
            attrs.append([name, value])
            i += 1
        for name, value in changes[m.group(1)].items():
            if value is None:
                attrs = [a for a in attrs if a[0] != name]
                continue
            hit = [a for a in attrs if a[0] == name]
            if hit:
                hit[0][1] = '"' + value + '"'
            else:
                attrs.append([name, '"' + value + '"'])
        for k, (name, value) in enumerate(attrs):
            out.append(indent + name + "=" + value + (close if k == len(attrs) - 1 else ""))
    return NL.join(out)


def vector(width, height, vw, vh, paths):
    body = ""
    for path in paths:
        data, color, stroke = path[:3]
        body += NL + "    <path" + NL + '        android:pathData="' + data + '"' + NL
        if len(path) > 3:
            body += '        android:' + ("strokeAlpha" if stroke else "fillAlpha") + '="' + path[3] + '"' + NL
        if stroke:
            body += ('        android:strokeColor="@color/' + color + '"' + NL
                     + '        android:strokeLineCap="round"' + NL
                     + '        android:strokeLineJoin="round"' + NL
                     + '        android:strokeWidth="' + stroke + '" />' + NL)
        else:
            body += '        android:fillColor="@color/' + color + '" />' + NL
    return ('<?xml version="1.0" encoding="utf-8"?>' + NL
            + "<!-- Only for the widget list's preview; the widget draws its own. -->" + NL
            + '<vector xmlns:android="http://schemas.android.com/apk/res/android"' + NL
            + '    android:width="' + width + '"' + NL
            + '    android:height="' + height + '"' + NL
            + '    android:viewportWidth="' + vw + '"' + NL
            + '    android:viewportHeight="' + vh + '">' + NL
            + body + "</vector>" + NL)


# A ring like WidgetArt.ring: stroke 12% of the size, filled clockwise from the top.
TRACK_CIRCLE = "M24,2.88 A21.12,21.12 0 1,1 24,45.12 A21.12,21.12 0 1,1 24,2.88"
write("drawable/widget_preview_ring.xml", vector("48dp", "48dp", "48", "48", [
    (TRACK_CIRCLE, "widget_track", "5.76"),
    ("M24,2.88 A21.12,21.12 0 0,1 41.09,36.42", "widget_title", "5.76"),  # 35%
]))
write("drawable/widget_preview_ring_most.xml", vector("48dp", "48dp", "48", "48", [
    (TRACK_CIRCLE, "widget_track", "5.76"),
    ("M24,2.88 A21.12,21.12 0 1,1 3.91,17.47", "widget_title", "5.76"),  # 80%
]))
# A bar like WidgetArt.bar: 480 by 16, filled from the right.
write("drawable/widget_preview_bar.xml", vector("150dp", "5dp", "480", "16", [
    ("M8,0 H472 A8,8 0 0,1 472,16 H8 A8,8 0 0,1 8,0 Z", "widget_track", None),
    ("M320,0 H472 A8,8 0 0,1 472,16 H320 A8,8 0 0,1 320,0 Z", "widget_title", None),  # 35%
]))

# «تم ✓» like WidgetArt.check: a ring with a faint check while open, a filled circle once done.
CHECK = "M13.44,24.96 L20.64,31.68 L34.56,17.28"
write("drawable/widget_preview_check.xml", vector("48dp", "48dp", "48", "48", [
    ("M24,1.92 A22.08,22.08 0 1,1 24,46.08 A22.08,22.08 0 1,1 24,1.92", "widget_title", "3.84"),
    (CHECK, "widget_title", "4.32", "0.4"),
]))
write("drawable/widget_preview_check_done.xml", vector("48dp", "48dp", "48", "48", [
    ("M24,0 A24,24 0 1,1 24,48 A24,24 0 1,1 24,0 Z", "widget_title", None),
    (CHECK, "widget_background", "4.32"),
]))

DATE = "الأربعاء 11/25"  # a Wednesday, the sample week's today
PLAN = "خطة الحفظ"
LESSON = "البقرة 11-20"
MINOR = "الفاتحة - البقرة 10"
MAJOR = "النبأ - النازعات"
COUNT = "220 من 637 جلسة"


def words(value):
    return {"android:text": value}


def picture(name):
    return {"android:src": "@drawable/" + name}


# The week: two days done, one missed, today, one to come, two without a session.
days = {}
for n, state in enumerate(["done", "done", "missed", "today", "upcoming", "idle", "idle"], 1):
    days["day%d_dot" % n] = {
        "done": {"android:tint": "@color/widget_title"},
        "missed": {"android:tint": "@color/widget_missed"},
        "today": {"android:tint": "@color/widget_today"},
        "upcoming": {"android:src": "@drawable/widget_dot_ring", "android:tint": "@color/widget_muted"},
        "idle": {"android:tint": "@color/widget_idle"},
    }[state]
    if state == "today":
        days["day%d_name" % n] = {"android:textColor": "@color/widget_today", "android:textStyle": "bold"}
row = preview(WRITTEN["layout/widget_week_row.xml"], days)
write("layout/widget_week_row_preview.xml", row.replace(
    "<!-- The days of this week, Sunday first: done, missed, today or still to come. -->",
    "<!-- The week strip as the widget list shows it, with sample days. -->"))


def preview_layout(kind, changes):
    xml = WRITTEN["layout/widget_" + kind + ".xml"]
    xml = xml.replace('layout="@layout/widget_week_row"', 'layout="@layout/widget_week_row_preview"')
    write("layout/widget_" + kind + "_preview.xml", preview(xml, changes))


preview_layout("list", {
    "widget_date": words(DATE),
    "plan1_ring": picture("widget_preview_ring"),
    "plan1_check": {"android:visibility": None, "android:src": "@drawable/widget_preview_check"},
    "plan1_percent": words("35"),
    "plan1_name": words(PLAN),
    "plan1_text": words("○ الحفظ: " + LESSON + "\\n✓ الصغرى: " + MINOR),
    "plan2": {"android:visibility": None},
    "plan2_ring": picture("widget_preview_ring_most"),
    "plan2_check": {"android:visibility": None, "android:src": "@drawable/widget_preview_check_done"},
    "plan2_percent": words("80"),
    "plan2_name": words("مراجعة جزء عمّ"),
    "plan2_text": words("○ الكبرى: " + MAJOR),
})
preview_layout("compact", {
    "compact_plan": words(PLAN + " · درس اليوم"),
    "compact_lesson": words(LESSON),
    "compact_detail": words("الصغرى: " + MINOR),
    "compact_bar": picture("widget_preview_bar"),
    "compact_check": {"android:visibility": None, "android:src": "@drawable/widget_preview_check"},
    "compact_count": words(COUNT),
})
preview_layout("ring", {
    "ring_ring": picture("widget_preview_ring"),
    "ring_percent": words("35%"),
    "ring_name": words(PLAN),
    "ring_count": words(COUNT),
})
preview_layout("week", {
    "week_lesson": words("درس اليوم: " + LESSON),
    "week_plan": words(PLAN),
})
preview_layout("board", {
    "board_plan": words(PLAN),
    "board_date": words(DATE),
    "board_ring": picture("widget_preview_ring"),
    "board_check": {"android:visibility": None, "android:src": "@drawable/widget_preview_check"},
    "board_percent": words("35%"),
    "board_lesson": words(LESSON),
    "board_items": words("○ الصغرى: " + MINOR + "\\n○ الكبرى: " + MAJOR),
    "board_footer": words(COUNT),
})
