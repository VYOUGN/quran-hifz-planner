"""Writes the home-screen widget layouts of «جدول الحفظ» into app/src/main/res."""
import io
import os
import sys

RES = sys.argv[1]
NL = "\n"


WRITTEN = {}


def write(rel, content):
    WRITTEN[rel] = content
    with io.open(os.path.join(RES, rel), "w", encoding="utf-8", newline=NL) as f:
        f.write(content)


write("drawable/widget_bg.xml", """<?xml version="1.0" encoding="utf-8"?>
<!-- A widget's background, tinted to the widget's colors by a color filter. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/widget_background" />
    <corners android:radius="@dimen/widget_radius" />
</shape>
""")

BG = """    <ImageView
        android:id="@+id/widget_bg"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:importantForAccessibility="no"
        android:scaleType="fitXY"
        android:src="@drawable/widget_bg" />
"""


def root(body):
    return ("""<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:layoutDirection="rtl">

""" + BG + NL + body + "</FrameLayout>" + NL)


def text(id, size, color, attrs=(), value=None, bold=False, indent="        "):
    lines = [
        indent + "<TextView",
        indent + '    android:id="@+id/' + id + '"',
        indent + '    android:layout_width="match_parent"',
        indent + '    android:layout_height="wrap_content"',
    ]
    lines += [indent + "    " + a for a in attrs]
    lines += [
        indent + '    android:textAlignment="viewStart"',
        indent + '    android:textColor="@color/' + color + '"',
        indent + '    android:textSize="' + str(size) + 'sp"',
    ]
    if value:
        lines.append(indent + '    android:text="' + value + '"')
    if bold:
        lines.append(indent + '    android:textStyle="bold"')
    lines[-1] += " />"
    return NL.join(lines) + NL


def ring(prefix, size, pct, indent):
    i = indent
    return NL.join([
        i + "<FrameLayout",
        i + '    android:layout_width="' + str(size) + 'dp"',
        i + '    android:layout_height="' + str(size) + 'dp">',
        "",
        i + "    <ImageView",
        i + '        android:id="@+id/' + prefix + '_ring"',
        i + '        android:layout_width="match_parent"',
        i + '        android:layout_height="match_parent"',
        i + '        android:importantForAccessibility="no"',
        i + '        android:scaleType="fitCenter" />',
        "",
        i + "    <TextView",
        i + '        android:id="@+id/' + prefix + '_percent"',
        i + '        android:layout_width="wrap_content"',
        i + '        android:layout_height="wrap_content"',
        i + '        android:layout_gravity="center"',
        i + '        android:textColor="@color/widget_text"',
        i + '        android:textSize="' + str(pct) + 'sp"',
        i + '        android:textStyle="bold" />',
        i + "</FrameLayout>",
    ]) + NL


def check(id, size, indent, margin=10, pad=4):
    """«تم ✓»: hidden until the widget has a session of today to tick."""
    i = indent
    return NL.join([
        i + "<ImageView",
        i + '    android:id="@+id/' + id + '"',
        i + '    android:layout_width="' + str(size) + 'dp"',
        i + '    android:layout_height="' + str(size) + 'dp"',
        i + '    android:layout_marginStart="' + str(margin) + 'dp"',
        i + '    android:background="?android:attr/selectableItemBackgroundBorderless"',
        i + '    android:contentDescription="@string/widget_done"',
        i + '    android:padding="' + str(pad) + 'dp"',
        i + '    android:scaleType="fitCenter"',
        i + '    android:visibility="gone" />',
    ]) + NL


def header(name_id, date_id, name_size, name_color):
    return """        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/""" + name_id + """"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:text="@string/widget_title"
                android:textAlignment="viewStart"
                android:textColor="@color/""" + name_color + """"
                android:textSize=\"""" + str(name_size) + """sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/""" + date_id + """"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:maxLines="1"
                android:textColor="@color/widget_muted"
                android:textSize="12sp" />
        </LinearLayout>
"""


# ---- list: today's session of up to three plans
blocks = ""
for n in (1, 2, 3):
    gone = "" if n == 1 else NL + '            android:visibility="gone"'
    blocks += NL + """        <LinearLayout
            android:id="@+id/plan""" + str(n) + """"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:gravity="center_vertical"
            android:orientation="horizontal\"""" + gone + """>

""" + ring("plan" + str(n), 36, 10, "            ") + """
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="10dp"
                android:layout_weight="1"
                android:orientation="vertical">

""" + text("plan" + str(n) + "_name", 13, "widget_text", ['android:ellipsize="end"', 'android:maxLines="1"'], bold=True, indent="                ") + NL \
        + text("plan" + str(n) + "_text", 12, "widget_muted", ['android:ellipsize="end"', 'android:maxLines="3"'], indent="                ") + """            </LinearLayout>

""" + check("plan" + str(n) + "_check", 34, "            ", 8, 3) + """        </LinearLayout>
"""

write("layout/widget_list.xml", root("""    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="14dp">

""" + header("widget_title", "widget_date", 15, "widget_title") + NL
    + text("widget_empty", 13, "widget_text", ['android:layout_marginTop="8dp"', 'android:visibility="gone"'], "@string/widget_empty")
    + blocks + "    </LinearLayout>" + NL))

# ---- compact: one plan's lesson, big, with a progress bar
write("layout/widget_compact.xml", root("""    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center_vertical"
        android:orientation="vertical"
        android:padding="14dp">

""" + text("compact_plan", 11, "widget_muted", ['android:ellipsize="end"', 'android:maxLines="1"'], "@string/widget_title") + """
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

""" + text("compact_lesson", 19, "widget_text", ['android:ellipsize="end"', 'android:maxLines="2"'], "—", True, indent="                ") + NL
    + text("compact_detail", 11, "widget_muted", ['android:layout_marginTop="2dp"', 'android:ellipsize="end"', 'android:maxLines="2"'], indent="                ") + """            </LinearLayout>

""" + check("compact_check", 40, "            ") + """        </LinearLayout>

        <ImageView
            android:id="@+id/compact_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:adjustViewBounds="true"
            android:importantForAccessibility="no"
            android:scaleType="fitCenter" />

""" + text("compact_count", 11, "widget_muted", ['android:layout_marginTop="4dp"', 'android:maxLines="1"'])
    + "    </LinearLayout>" + NL))

# ---- ring: one plan's progress
write("layout/widget_ring.xml", root("""    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="10dp">

""" + ring("ring", 76, 17, "        ") + """
        <TextView
            android:id="@+id/ring_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="@color/widget_text"
            android:textSize="12sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/ring_count"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:textColor="@color/widget_muted"
            android:textSize="11sp" />
    </LinearLayout>
"""))

# ---- the week row: seven days, Sunday first, a letter and a dot each
cells = ""
for n, letter in enumerate(["ح", "ن", "ث", "ر", "خ", "ج", "س"], 1):
    cells += NL + """    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center_horizontal"
        android:orientation="vertical">

        <TextView
            android:id="@+id/day""" + str(n) + """_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text=\"""" + letter + """"
            android:includeFontPadding="false"
            android:textColor="@color/widget_muted"
            android:textSize="10sp" />

        <ImageView
            android:id="@+id/day""" + str(n) + """_dot"
            android:layout_width="11dp"
            android:layout_height="11dp"
            android:layout_marginTop="3dp"
            android:importantForAccessibility="no"
            android:src="@drawable/widget_dot" />
    </LinearLayout>
"""
write("layout/widget_week_row.xml", """<?xml version="1.0" encoding="utf-8"?>
<!-- The days of this week, Sunday first: done, missed, today or still to come. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/week_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">
""" + cells + "</LinearLayout>" + NL)

INCLUDE = """        <include
            layout="@layout/widget_week_row"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="{top}dp" />
"""

# ---- week: today's lesson over the days of the week
write("layout/widget_week.xml", root("""    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center_vertical"
        android:orientation="vertical"
        android:paddingHorizontal="14dp"
        android:paddingVertical="6dp">

""" + header("week_lesson", "week_plan", 13, "widget_text") + NL + INCLUDE.format(top=4) + "    </LinearLayout>" + NL))

# ---- board: everything about one plan
write("layout/widget_board.xml", root("""    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="16dp">

""" + header("board_plan", "board_date", 15, "widget_title") + """
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

""" + ring("board", 64, 14, "            ") + """
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="14dp"
                android:layout_weight="1"
                android:orientation="vertical">

""" + text("board_heading", 11, "widget_muted", ['android:maxLines="1"'], "@string/widget_title", indent="                ") + NL
    + text("board_lesson", 18, "widget_text", ['android:ellipsize="end"', 'android:maxLines="2"'], bold=True, indent="                ") + NL
    + text("board_items", 12, "widget_muted", ['android:layout_marginTop="2dp"', 'android:ellipsize="end"', 'android:maxLines="3"'], indent="                ")
    + """            </LinearLayout>

""" + check("board_check", 40, "            ") + """        </LinearLayout>

        <!-- Takes the room left over, so the week and the count sit at the bottom of a tall widget. -->
        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

""" + INCLUDE.format(top=12) + NL
    + text("board_footer", 11, "widget_muted", ['android:layout_marginTop="8dp"', 'android:ellipsize="end"', 'android:maxLines="1"'])
    + "    </LinearLayout>" + NL))

exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "gen_widget_previews.py"), encoding="utf-8").read())

print("ok")
