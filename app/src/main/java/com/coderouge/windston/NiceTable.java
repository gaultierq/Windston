package com.coderouge.windston;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.RequiresApi;

import java.util.List;

public class NiceTable extends TableLayout {

    private int mCellRes = R.layout.default_nice_table_cell;
    private List<String> mHeaders;
    private List<List<String>> mData;
    private List<String> mCNames;

    public NiceTable(Context context) {
        super(context);
    }

    public NiceTable(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NiceTable);
        final int N = a.getIndexCount();
        for (int i = 0; i < N; ++i) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.NiceTable_cell:
                    mCellRes = a.getResourceId(attr, R.layout.default_nice_table_cell);
                    break;
            }
        }
        a.recycle();
    }

    public void setHeaders(List<String> headers) {
        mHeaders = headers;
    }

    public void setColumnNames(List<String> cNames) {
        mCNames = cNames;
    }

    public void setData(List<List<String>> data) {
        mData = data;
    }

    public void refresh() {
        removeAllViews();
        if (mHeaders != null) {
            TableRow row = new TableRow(getContext());
            for (String h : mHeaders) {
                TextView tv = (TextView) inflate(getContext(), mCellRes, null);
                tv.setText(h);
                row.addView(tv);
            }
            addView(row);
        }
        for (int i = 0; i < mData.size(); i++) {
            List<String> rows = mData.get(i);

            TableRow row = new TableRow(getContext());
            if (mCNames != null && i < mCNames.size()) {
                String cNam = mCNames.get(i);
                TextView tv = (TextView) inflate(getContext(), mCellRes, null);
                tv.setText(cNam);
                row.addView(tv);
            }

            for (String cell : rows) {
                TextView tv = (TextView) inflate(getContext(), mCellRes, null);
                tv.setText(cell);
                row.addView(tv);
            }
            addView(row);
        }
    }


}
