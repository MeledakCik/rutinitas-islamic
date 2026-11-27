package com.rutinitas.islamic;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Widget layar utama hanya boleh memakai view yang didukung RemoteViews (minSdk 24).
 * View lain, misalnya &lt;View&gt; polos, membuat launcher menampilkan "Masalah saat memuat widget".
 */
public class WidgetLayoutTest {
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
            "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView", "ProgressBar", "TextView",
            "ViewFlipper", "ListView", "GridView", "StackView", "AdapterViewFlipper", "ViewStub"));

    private static final String[] WIDGET_LAYOUTS = {
            "routine_widget.xml", "prayer_widget.xml", "prayer_widget_tall.xml", "dzikir_widget.xml"};

    @Test public void widgetLayoutsUseOnlyRemoteViewsSupportedViews() throws Exception {
        File layoutDir = new File("src/main/res/layout");
        if (!layoutDir.isDirectory()) layoutDir = new File("app/src/main/res/layout");
        for (String name : WIDGET_LAYOUTS) {
            File file = new File(layoutDir, name);
            assertTrue("Layout widget tidak ditemukan: " + file, file.isFile());
            Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getDocumentElement();
            check(name, root);
        }
    }

    private static void check(String file, Element element) {
        assertTrue(file + ": <" + element.getTagName() + "> tidak didukung di widget",
                ALLOWED.contains(element.getTagName()));
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) check(file, (Element) child);
        }
    }
}
