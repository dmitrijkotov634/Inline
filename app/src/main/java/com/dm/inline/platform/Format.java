package com.dm.inline.platform;

import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import android.content.ClipboardManager;
import android.content.ClipData;

public class Format extends LuaTable {

    public ClipboardManager clipboard;

    public Format(ClipboardManager manager) {
        this.clipboard = manager;

        set("fromhtml", new fromHtml());
        set("fromclipboard", new FromClipboardFlag());
    }

    public class fromHtml extends OneArgFunction {
        public LuaValue call(LuaValue html) {
            clipboard.setPrimaryClip(ClipData.newHtmlText(null, "", html.tojstring()));
            return new FromClipboardFlag();
        }
    }

    public class FromClipboardFlag extends LuaValue {
        public int type() {
            return 10;
        }

        public String typename() {
            return "formatflag";
        }
    }
}
