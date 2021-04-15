package com.dm.inline;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;
import java.util.List;

public class utils extends TwoArgFunction {

    public utils() {}

    public LuaValue call(LuaValue name, LuaValue env) {
        LuaValue library = tableOf();
        library.set("getargs", new getArgs());
        library.set("split", new split());

        env.set("utils", library);
        return library;
    }

    public class getArgs extends OneArgFunction {
        public LuaValue call(LuaValue string) {
            LuaTable value = new LuaTable();

            int index = 1;
            for (String arg : ArgumentTokenizer.tokenize(string.tojstring())) {
                value.set(index++, arg);
            }

            return value;
        }
    }

    public class split extends ThreeArgFunction {
        public LuaValue call(LuaValue string, LuaValue pattern, LuaValue maxCount) {
            LuaTable value = new LuaTable();

            int index = 1;
            for (String sub : string.tojstring().split(pattern.isnil() ? " " : pattern.tojstring(),
                                                      maxCount.isnil() ? -1 : maxCount.toint())) {
                value.set(index++, sub);
            }

            return value;
        }
    }
}
