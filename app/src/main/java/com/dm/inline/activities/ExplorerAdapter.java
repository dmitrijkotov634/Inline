package com.dm.inline.activities;

import android.widget.BaseAdapter;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.content.Context;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaValue;
import java.util.ArrayList;
import java.util.HashMap;
import com.dm.inline.R;

public class ExplorerAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<Varargs> data;

    public TextView key;
    public TextView string;

    public ExplorerAdapter(Context context, ArrayList<Varargs> data) {
        this.context = context;
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Varargs getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = LayoutInflater.from(context).inflate(R.layout.ExploreItem, parent, false);

        key = convertView.findViewById(R.id.key);
        string = convertView.findViewById(R.id.string);

        switch (data.get(position).arg(2).type()) {
            case LuaValue.TSTRING:
                convertView.setBackgroundColor(0xFFEAABAB);
                key.setTextColor(0xFFC00000);
                break;
            case LuaValue.TNUMBER:
                convertView.setBackgroundColor(0xFFB2E7AB);
                key.setTextColor(0xFF15B500);
                break;
            case LuaValue.TFUNCTION:
                convertView.setBackgroundColor(0xFFABABD5);
                key.setTextColor(0xFF000081);
                break;
            case LuaValue.TTABLE:
                convertView.setBackgroundColor(0xFFEAE2AB);
                key.setTextColor(0xFFBFA600);
                break;
            case LuaValue.TBOOLEAN:
                convertView.setBackgroundColor(0xFFCFABEA);
                key.setTextColor(0xFF6F00BF);
                break;
            case LuaValue.TNIL:
                convertView.setBackgroundColor(0xFFCDCDCD);
                key.setTextColor(0xFF676767);
                break;
            default:
                convertView.setBackgroundColor(0xFFABE5D6);
                key.setTextColor(0xFF00AF82);
        }

        key.setText(data.get(position).arg1().tojstring());
        string.setText(data.get(position).arg(2).tojstring());
        return convertView;
    }
}
