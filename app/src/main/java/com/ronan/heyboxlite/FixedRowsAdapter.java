package com.ronan.heyboxlite;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.WrapperListAdapter;

/**
 * Fixed chrome stays in the scrolling list, without the framework's mutable
 * HeaderViewListAdapter footer indexing during a data change.
 */
final class FixedRowsAdapter extends BaseAdapter implements WrapperListAdapter {
    private final ListAdapter content;
    private final View[] headers;
    private final View footer;

    FixedRowsAdapter(ListAdapter content, View footer, View... headers) {
        this.content = content;
        this.footer = footer;
        this.headers = headers.clone();
    }

    @Override public ListAdapter getWrappedAdapter() { return content; }
    @Override public int getCount() { return headers.length + content.getCount() + 1; }
    @Override public boolean areAllItemsEnabled() { return false; }
    @Override public boolean hasStableIds() { return false; }
    @Override public boolean isEmpty() { return false; }
    @Override public int getViewTypeCount() { return content.getViewTypeCount(); }

    @Override public boolean isEnabled(int position) {
        int index = position - headers.length;
        return index >= 0 && index < content.getCount() && content.isEnabled(index);
    }

    @Override public Object getItem(int position) {
        int index = position - headers.length;
        return index >= 0 && index < content.getCount() ? content.getItem(index) : null;
    }

    @Override public long getItemId(int position) {
        int index = position - headers.length;
        return index >= 0 && index < content.getCount() ? content.getItemId(index) : -1L;
    }

    @Override public int getItemViewType(int position) {
        int index = position - headers.length;
        return index >= 0 && index < content.getCount()
                ? content.getItemViewType(index) : AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
    }

    @Override public View getView(int position, View reusable, ViewGroup parent) {
        if (position < headers.length) return headers[position];
        int index = position - headers.length;
        if (index == content.getCount()) return footer;
        return content.getView(index, reusable, parent);
    }

    @Override public void registerDataSetObserver(DataSetObserver observer) {
        content.registerDataSetObserver(observer);
    }

    @Override public void unregisterDataSetObserver(DataSetObserver observer) {
        content.unregisterDataSetObserver(observer);
    }
}
