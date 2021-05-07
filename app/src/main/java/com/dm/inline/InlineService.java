package com.dm.inline;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.Gravity;
import android.content.res.AssetManager;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ClipData;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Build;
import android.os.Looper;
import android.os.Handler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;
import java.util.Timer;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.Globals;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class InlineService extends AccessibilityService {

    private static InlineService sharedInstance;

    public ClipboardManager clipboard;
    public Bundle arg;

    public Globals env;
    public SharedPreferences db;

    public HashSet<String> defaultPath = new HashSet<String>();
    public HashMap<LuaValue, LuaValue> watchers = new HashMap<LuaValue, LuaValue>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

		AccessibilityServiceInfo info = new AccessibilityServiceInfo();

        info.flags = AccessibilityServiceInfo.DEFAULT |
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
		setServiceInfo(info);

        defaultPath.add("/assets/modules/");
        defaultPath.add("/sdcard/inline/");
        defaultPath.add("/sdcard/.inline/");

        sharedInstance = this;

        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        db = getSharedPreferences("db", MODE_PRIVATE);

        env = JsePlatform.standardGlobals();

        LuaValue loader = new loadModules();

        LuaTable inline = new LuaTable();
        inline.set("toast", new toast());
        inline.set("timer", new InlineTimer());
        inline.set("context", new InlineContext());
        inline.set("loadmodules", loader);

        inline.set("watchers", new WatchersTable());
        inline.set("clipboard", new ClipboardTable());
        inline.set("db", new DBTable());
        inline.set("fmt", new FormatTable());

        env.set("inline", inline);
        env.set("cake", LuaValue.FALSE);

        loader.call();
    }

	public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();

        for (LuaValue watcher : watchers.values()) {
            try {
                watcher.call(new InlineContext(node));
            } catch (Exception e) {
                logError(e);
            }
        }

        if (Build.VERSION.SDK_INT >= 26 ? !node.isShowingHintText() : true) {

            String text = node.getText() == null ? "" : node.getText().toString();
            Matcher m = Pattern.compile("\\{.+\\}\\$", Pattern.DOTALL).matcher(text);

            expressions:
            while (m.find()) {
                String[] args = m.group(0).substring(1, m.group(0).length() - 2).split(" ", 2);

                if (args.length > 0) {
                    LuaValue value = env;

                    for (String path : args[0].split("\\.")) {
                        if ((value = value.get(path)).isnil())
                            break;
                    }

                    try {
                        find:
                        while (true) {
                            switch (value.type()) {
                                case LuaValue.TFUNCTION:
                                    LuaValue context = new InlineContext(node);
                                    value = args.length == 1 ? value.call(context) : value.call(context, LuaValue.valueOf(args[1]));
                                    break;

                                case LuaValue.TNUMBER:
                                case LuaValue.TSTRING:
                                case LuaValue.TBOOLEAN:
                                    text = text.replace(m.group(0), value.tojstring());
                                    break find;

                                case 10:
                                    arg = new Bundle();
                                    arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, m.start());
                                    arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, m.end());
                                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arg);
                                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE);

                                default:
                                    continue expressions;
                            }
                        }

                        arg = new Bundle();
                        arg.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arg);

                        arg = new Bundle();
                        arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, node.getTextSelectionStart());
                        arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, node.getTextSelectionEnd());
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arg);

                    } catch (Exception e) {
                        logError(e);
                    }
                }
            }
        }
    }

    public void onInterrupt() {}

    public boolean onUnbind(Intent intent) {
        sharedInstance = null;
        return super.onUnbind(intent);
    }

    public static InlineService getSharedInstance() {
        return sharedInstance;
    }

    public void logError(Exception e) {
        Toast toast = Toast.makeText(this, e.toString(), 1);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        e.printStackTrace();
    }

    public class InlineContext extends LuaTable {
        public AccessibilityNodeInfo node;

        LuaValue settext = new setText();
        LuaValue gettext = new getText();
        LuaValue setselection = new setSelection();
        LuaValue getselection = new getSelection();
        LuaValue cut = new cut();
        LuaValue copy = new copy();
        LuaValue paste = new paste();
        LuaValue isfocused = new isFocused();
        LuaValue ismultiline = new isMultiLine();
        LuaValue isshowinghinttext = new isShowingHintText();
        LuaValue refresh = new refresh();
        LuaValue getpackage = new getPackage();

        public InlineContext(AccessibilityNodeInfo node) {
            this();
            this.node = node;
        }

        public InlineContext() {
            set("settext", settext);
            set("gettext", gettext);
            set("setselection", setselection);
            set("getselection", getselection);
            set("cut", cut);
            set("copy", copy);
            set("paste", paste);
            set("isfocused", isfocused);
            set("ismultiline", ismultiline);
            set("isshowinghinttext", isshowinghinttext);
            set("refresh", refresh);
            set("getpackage", getpackage);
        }


        public class setText extends TwoArgFunction {
            public LuaValue call(LuaValue self, LuaValue text) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.isnil() ? "" : text.tojstring());
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

                return NIL;
            }
        }

        public class getText extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                CharSequence text = ((InlineContext)self).node.getText();
                return valueOf(text == null ? "" : text.toString());
            }
        }

        public class setSelection extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue start, LuaValue end) {
                Bundle args = new Bundle();
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start.toint() - 1);
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end.isnil() ? start.toint() - 1 : end.toint() - 1);
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
                return NIL;
            }
        }

        public class getSelection extends VarArgFunction {
            public Varargs invoke(Varargs v) {
                return varargsOf(valueOf(((InlineContext)v.arg1()).node.getTextSelectionStart() + 1), 
                                 valueOf(((InlineContext)v.arg1()).node.getTextSelectionEnd() + 1));
            }
        }

        public class cut extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_CUT);
                return NIL;
            }
        }

        public class copy extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_COPY);
                return NIL;
            }
        }

        public class paste extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                return NIL;
            }
        }

        public class isMultiLine extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(((InlineContext)self).node.isMultiLine());
            }
        }

        public class isShowingHintText extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(Build.VERSION.SDK_INT >= 26 ? ((InlineContext)self).node.isShowingHintText() : false);
            }
        }

        public class isFocused extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(((InlineContext)self).node.isFocused());
            }
        }

        public class refresh extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.refresh();
                return NIL;
            }
        }

        public class getPackage extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(((InlineContext)self).node.getPackageName().toString());
            }
        }
    }

    public class InlineTimer extends LuaTable {
        public Timer timer;

        LuaValue schedule = new schedule();
        LuaValue cancel = new cancel();

        public InlineTimer() {
            this.timer = new Timer();

            set("schedule", schedule);
            set("cancel", cancel);

            LuaTable m = new LuaTable();
            m.set(CALL, new call());
            this.setmetatable(m);
        }

        public class call extends ZeroArgFunction {
            public LuaValue call() {
                return (LuaValue) new InlineTimer();
            }
        }

        public class cancel extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineTimer)self).timer.cancel();
                return NIL;
            }
        }

        public class schedule extends VarArgFunction {
            public Varargs invoke(final Varargs v) {
                TimerTask task = new TimerTask() {
                    public void run() {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    v.arg(2).call();
                                }});
                    }
                };

                if (v.arg(4).isnil())
                    ((InlineTimer)v.arg1()).timer.schedule(task, v.arg(3).toint());
                else
                    ((InlineTimer)v.arg1()).timer.scheduleAtFixedRate(task, v.arg(3).toint(), v.arg(4).toint());

                return NIL;
            }
        }
    }

    public class toast extends OneArgFunction {
        public LuaValue call(LuaValue text) {
            Toast.makeText(getApplicationContext(), text.tojstring(), 1).show();
            return NIL;
        }
    }

    public class loadModules extends ZeroArgFunction {
        public void loadFile(String path, File file) throws IOException {
            BufferedReader reader = new BufferedReader(new FileReader(file));

            reader.mark(1);
            int ch = reader.read();
            if (ch != 65279)
                reader.reset();

            String rpath;
            prepare(env.load(reader, rpath = path + file.getName()).call(), rpath);

        }

        public void loadAsset(String path, AssetManager assets) throws IOException {
            InputStream stream = assets.open(path);
            byte[] buffer = new byte[stream.available()];
            stream.read(buffer);

            String rpath;
            prepare(env.load(new String(buffer), rpath = "/assets/" + path).call(), rpath);
        }

        public void prepare(LuaValue value, String path) {
            if (value.isfunction())
                watchers.put(valueOf(path), value);
        }

        public LuaValue call() {
            watchers.clear();

            try {
                for (String path : db.getStringSet("path", defaultPath))
                    if (path.startsWith("/assets/")) {
                        AssetManager assets = getResources().getAssets();
                        String assetPath = path.substring(8);
                        String[] files = assets.list(assetPath.substring(0, assetPath.length() - 1));

                        if (files.length > 0)
                            for (String file : files)
                                loadAsset(assetPath + file, assets);
                        else
                            loadAsset(assetPath, assets);
                    } else {
                        File module = new File(path);

                        if (module.isDirectory()) {
                            for (File file : module.listFiles())
                                if (file.isFile())
                                    loadFile(path, file);
                        } else if (module.isFile()) {
                            loadFile(path, module);
                        }
                    }
            } catch (Exception e) {
                logError(e);
            }
            return TRUE;
        }
    }

    public class WatchersTable extends LuaTable {
        public WatchersTable() {
            LuaTable m = new LuaTable();
            m.set(INDEX, new get());
            m.set(NEWINDEX, new set());
            m.set(LEN, new len());

            this.setmetatable(m);
        }

        public class get extends TwoArgFunction {
            public LuaValue call(LuaValue table, LuaValue name) {
                return watchers.containsKey(name) ? watchers.get(name) : NIL;
            }
        }

        public class len extends ZeroArgFunction {
            public LuaValue call() {
                return valueOf(watchers.size());
            }
        }

        public class set extends ThreeArgFunction {
            public LuaValue call(LuaValue table, LuaValue name, LuaValue function) {
                if (function.isnil()) {
                    if (watchers.containsKey(name))
                        watchers.remove(name);
                } else {
                    watchers.put(name, function);
                }

                return NIL;
            }
        }
    }

    public class ClipboardTable extends LuaTable {
        public ClipboardTable() {
            set("sethtml", new setHtml());
            set("set", new setClip());
            set("get", new getClip());
            set("has", new hasClip());
            set("clear", new clearClip());
        }

        public class setHtml extends TwoArgFunction {
            public LuaValue call(LuaValue text, LuaValue html) {
                clipboard.setPrimaryClip(ClipData.newHtmlText(null, text.tojstring(), html.tojstring()));
                return NIL;
            }
        }

        public class setClip extends OneArgFunction {
            public LuaValue call(LuaValue text) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, text.tojstring()));
                return NIL;
            }
        }

        public class getClip extends ZeroArgFunction {
            public LuaValue call() {
                ClipData clip = clipboard.getPrimaryClip();
                return valueOf(clip.getItemAt(0).coerceToHtmlText(getApplicationContext()));
            }
        }

        public class hasClip extends ZeroArgFunction {
            public LuaValue call() {
                return valueOf(clipboard.hasPrimaryClip());
            }
        }

        public class clearClip extends ZeroArgFunction {
            public LuaValue call() {
                clipboard.clearPrimaryClip();
                return NIL;
            }
        }
    }

    public class FormatTable extends LuaTable {
        public FormatTable() {
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

    public class DBTable extends LuaTable {
        public DBTable() {
            LuaValue put = new putKey();
            set("put", put);
            set("contains", new contains());
            set("getstring", new getString());
            set("getint", new getInt());
            set("getfloat", new getFloat());
            set("getlong", new getLong());
            set("getboolean", new getBoolean());
            set("getstringset", new getStringSet());

            LuaTable m = new LuaTable();
            m.set(LuaValue.INDEX, new getStringDefault());
            m.set(LuaValue.NEWINDEX, put);

            this.setmetatable(m);
        }

        public class getStringDefault extends TwoArgFunction {
            public LuaValue call(LuaValue table, LuaValue key) {
                return valueOf(db.getString(key.tojstring(), ""));
            }
        }

        public class getString extends TwoArgFunction {
            public LuaValue call(LuaValue key, LuaValue defaultVal) {
                return valueOf(db.getString(key.tojstring(), defaultVal.isstring() ? defaultVal.tojstring() : ""));
            }
        }

        public class getStringSet extends TwoArgFunction {
            public LuaValue call(LuaValue key, LuaValue defaultVal) {
                HashSet<String> items = new HashSet<String>();

                if (defaultVal.istable()) {
                    LuaValue k = NIL;
                    while (true) {
                        Varargs n = defaultVal.next(k);
                        if ((k = n.arg1()).isnil() || !k.isint())
                            break;
                        LuaValue v = n.arg(2);

                        items.add(v.tojstring());
                    }
                }

                LuaTable table = new LuaTable();
                Set<String> set = db.getStringSet(key.tojstring(), items);

                int index = 1;
                for (String item : set) {
                    table.set(index++, valueOf(item));
                }

                return table;
            }
        }

        public class getInt extends TwoArgFunction {
            public LuaValue call(LuaValue key, LuaValue defaultVal) {
                return valueOf(db.getInt(key.tojstring(), defaultVal.isnumber() ? defaultVal.toint() : 0));
            }
        }

        public class getFloat extends TwoArgFunction {
            public LuaValue call(LuaValue key, LuaValue defaultVal) {
                return valueOf(db.getFloat(key.tojstring(), defaultVal.isnumber() ? defaultVal.tofloat() : 0));
            }
        }

        public class getLong extends TwoArgFunction {
            public LuaValue call(LuaValue key, LuaValue defaultVal) {
                return valueOf(db.getLong(key.tojstring(), defaultVal.isnumber() ? defaultVal.tolong() : 0));
            }
        }

        public class getBoolean extends TwoArgFunction {
            public LuaValue call(LuaValue key, LuaValue defaultVal) {
                return valueOf(db.getBoolean(key.tojstring(), defaultVal.toboolean()));
            }
        }

        public class contains extends OneArgFunction {
            public LuaValue call(LuaValue key) {
                return valueOf(db.contains(key.tojstring()));
            }
        }

        public class putKey extends ThreeArgFunction {
            public LuaValue call(LuaValue table, LuaValue key, LuaValue value) {
                if (key.isstring())
                    if (value.isnil())
                        db.edit().remove(key.tojstring()).apply();
                    else
                    if (value.isnumber()) {
                        if (value.isint())
                            db.edit().putInt(key.tojstring(), value.toint()).apply();
                        if (value.islong())
                            db.edit().putLong(key.tojstring(), value.tolong()).apply();
                        else
                            db.edit().putFloat(key.tojstring(), value.tofloat()).apply();

                    } else if (value.istable()) {
                        HashSet<String> items = new HashSet<String>();

                        LuaValue k = NIL;
                        while (true) {
                            Varargs n = value.next(k);
                            if ((k = n.arg1()).isnil() || !k.isint())
                                break;
                            LuaValue v = n.arg(2);

                            items.add(v.tojstring());
                        }

                        db.edit().putStringSet(key.tojstring(), items).apply();

                    } else if (value.isboolean())
                        db.edit().putBoolean(key.tojstring(), value.toboolean()).apply();
                    else
                        db.edit().putString(key.tojstring(), value.tojstring()).apply();

                return NIL;
            }
        }
    }
}
