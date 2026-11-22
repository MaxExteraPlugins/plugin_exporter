package com.exteraplugins.max_cactus_export;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {

    private static final String TAG = "MaxCactusExport";
    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static void load() {
        Log.d(TAG, "MaxCactusExport DEX loaded");
    }

    public static class ItemInfo {
        public final String id;
        public final String name;
        public final String subtext;

        public ItemInfo(String id, String name, String subtext) {
            this.id = id;
            this.name = name;
            this.subtext = subtext;
        }
    }

    public static void pyShowSheet(
            BaseFragment fragment,
            boolean isExport,
            String itemsJson,
            String stringsJson,
            Utilities.Callback<String> callback
    ) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        try {
            List<ItemInfo> items = new ArrayList<ItemInfo>();
            if (itemsJson != null && itemsJson.length() > 0) {
                JSONArray arr = new JSONArray(itemsJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    items.add(new ItemInfo(
                            obj.getString("id"),
                            obj.optString("name", obj.getString("id")),
                            obj.optString("subtext", "")
                    ));
                }
            }

            JSONObject stringsObj = null;
            if (stringsJson != null && stringsJson.length() > 0) {
                stringsObj = new JSONObject(stringsJson);
            }

            ImportExportSheet sheet = new ImportExportSheet(
                    fragment, isExport, items, stringsObj, callback
            );
            sheet.show();
        } catch (Throwable t) {
            Log.e(TAG, "Error in pyShowSheet", t);
        }
    }

    // ─── Native Telegram Bottom Sheet ──────────────────────────────────────────

    public static class ImportExportSheet extends BottomSheet {

        private final BaseFragment fragment;
        private final boolean isExport;
        private final List<ItemInfo> items;
        private final JSONObject strings;
        private final Set<String> selected = new HashSet<String>();
        private final Utilities.Callback<String> callback;

        private TextView actionBtn;
        private TextView selectAllBtn;
        private Adapter adapter;
        private boolean includeSettings = false;
        private boolean includeData = false;
        private boolean includePinned = false;

        public ImportExportSheet(
                BaseFragment fragment,
                boolean isExport,
                List<ItemInfo> items,
                JSONObject strings,
                Utilities.Callback<String> callback
        ) {
            super(fragment.getParentActivity(), false, fragment.getResourceProvider());
            this.fragment = fragment;
            this.isExport = isExport;
            this.items = items != null ? items : new ArrayList<ItemInfo>();
            this.strings = strings != null ? strings : new JSONObject();
            this.callback = callback;

            for (ItemInfo item : this.items) {
                selected.add(item.id);
            }

            buildUI(fragment.getParentActivity(), fragment);
        }

        @Override
        public boolean canDismissWithSwipe() {
            return false;
        }

        private String s(String key) {
            if (strings != null && strings.has(key)) {
                return strings.optString(key, key);
            }
            return key;
        }

        private static LinearLayout.LayoutParams lp(int w, int h) {
            return new LinearLayout.LayoutParams(w, h);
        }

        private void buildUI(Context ctx, final BaseFragment fragment) {
            setApplyTopPadding(false);
            setApplyBottomPadding(false);
            setCanDismissWithSwipe(false);
            setCanDismissWithTouchOutside(true);

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);

            // 1. Drag Handle
            View handle = new View(ctx);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp, fragment.getResourceProvider()));
            handleBg.setCornerRadius(AndroidUtilities.dp(2));
            handle.setBackground(handleBg);
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(4));
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.topMargin = AndroidUtilities.dp(10);
            handleLp.bottomMargin = AndroidUtilities.dp(10);
            root.addView(handle, handleLp);

            // 2. Header (Title + Select All)
            LinearLayout header = new LinearLayout(ctx);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(6));

            boolean isDark = Theme.isCurrentThemeDark();
            int titleColor = isDark ? 0xFFFFFFFF : 0xFF111111;

            TextView title = new TextView(ctx);
            title.setText(isExport ? s("export_alert_title") : s("import_alert_title"));
            title.setTextSize(19);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(titleColor);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, WC, 1f);
            header.addView(title, titleLp);

            selectAllBtn = new TextView(ctx);
            selectAllBtn.setTextSize(14);
            selectAllBtn.setTypeface(null, Typeface.BOLD);
            selectAllBtn.setTextColor(isDark ? 0xFF64B5F6 : Theme.getColor(Theme.key_dialogTextBlue2, fragment.getResourceProvider()));
            selectAllBtn.setBackground(Theme.getSelectorDrawable(false));
            selectAllBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
            selectAllBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selected.size() == items.size()) {
                        selected.clear();
                    } else {
                        for (ItemInfo it : items) {
                            selected.add(it.id);
                        }
                    }
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    updateActionButton();
                    updateSelectAllBtn();
                }
            });
            header.addView(selectAllBtn, lp(WC, WC));
            root.addView(header, lp(MP, WC));

            // 3. Info text only
            addInfoRow(ctx, root, isExport ? s("export_info") : s("import_info"), isDark ? 0xFFB0B0B0 : Theme.key_dialogTextGray2, 13, fragment);

            // Divider before list
            root.addView(makeDivider(ctx, fragment), lp(MP, 1));

            // 4. Standard RecyclerView (no Telegram RecyclerListView auto-alpha hacks)
            RecyclerView listView = new RecyclerView(ctx);
            listView.setLayoutManager(new LinearLayoutManager(ctx));
            listView.setVerticalScrollBarEnabled(true);
            listView.setItemAnimator(null);
            adapter = new Adapter(ctx, fragment);
            listView.setAdapter(adapter);

            int listH = Math.min(AndroidUtilities.dp(50) * Math.max(items.size(), 1),
                    (int) (AndroidUtilities.displaySize.y * 0.38f));
            root.addView(listView, lp(MP, Math.max(listH, AndroidUtilities.dp(90))));

            // Divider after list
            root.addView(makeDivider(ctx, fragment), lp(MP, 1));

            // 5. Options: Settings (default OFF)
            final CheckBoxCell settingsCheck = new CheckBoxCell(ctx, 4, 21, fragment.getResourceProvider());
            String settingsSub = isExport ? s("export_settings_sub") : s("import_settings_sub");
            settingsCheck.setText(s("include_settings"), settingsSub, includeSettings, true);
            settingsCheck.setBackground(Theme.getSelectorDrawable(false));
            styleCell(settingsCheck, fragment);
            settingsCheck.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    includeSettings = !includeSettings;
                    settingsCheck.setChecked(includeSettings, true);
                    styleCell(settingsCheck, fragment);
                    if (includeSettings) {
                        showWarningNotice(s("export_warning"));
                    }
                }
            });
            root.addView(settingsCheck, lp(MP, AndroidUtilities.dp(48)));

            // Options: Data (default OFF)
            final CheckBoxCell dataCheck = new CheckBoxCell(ctx, 4, 21, fragment.getResourceProvider());
            String dataSub = isExport ? s("include_plugin_data_sub") : s("import_data_sub");
            dataCheck.setText(s("include_plugin_data"), dataSub, includeData, true);
            dataCheck.setBackground(Theme.getSelectorDrawable(false));
            styleCell(dataCheck, fragment);
            dataCheck.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    includeData = !includeData;
                    dataCheck.setChecked(includeData, true);
                    styleCell(dataCheck, fragment);
                    if (includeData) {
                        showWarningNotice(s("export_warning"));
                    }
                }
            });
            root.addView(dataCheck, lp(MP, AndroidUtilities.dp(48)));

            // Options: Pinned state (default OFF)
            final CheckBoxCell pinnedCheck = new CheckBoxCell(ctx, 4, 21, fragment.getResourceProvider());
            String pinnedSub = isExport ? s("export_pinned_sub") : s("import_pinned_sub");
            pinnedCheck.setText(s("include_pinned"), pinnedSub, includePinned, false);
            pinnedCheck.setBackground(Theme.getSelectorDrawable(false));
            styleCell(pinnedCheck, fragment);
            pinnedCheck.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    includePinned = !includePinned;
                    pinnedCheck.setChecked(includePinned, true);
                    styleCell(pinnedCheck, fragment);
                }
            });
            root.addView(pinnedCheck, lp(MP, AndroidUtilities.dp(48)));

            // Divider before button
            root.addView(makeDivider(ctx, fragment), lp(MP, 1));

            // 6. Bottom Action Button
            LinearLayout bottomBar = new LinearLayout(ctx);
            bottomBar.setOrientation(LinearLayout.VERTICAL);
            bottomBar.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            actionBtn = new TextView(ctx);
            actionBtn.setGravity(Gravity.CENTER);
            actionBtn.setTextSize(15);
            actionBtn.setTypeface(null, Typeface.BOLD);
            actionBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, fragment.getResourceProvider()));
            actionBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton, fragment.getResourceProvider()),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, fragment.getResourceProvider())
            ));
            actionBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selected.isEmpty()) return;
                    try {
                        JSONObject res = new JSONObject();
                        JSONArray selArr = new JSONArray();
                        for (ItemInfo it : items) {
                            if (selected.contains(it.id)) {
                                selArr.put(it.id);
                            }
                        }
                        res.put("selected", selArr);
                        res.put("includeSettings", includeSettings);
                        res.put("includeData", includeData);
                        res.put("includePinned", includePinned);

                        dismiss();
                        if (callback != null) {
                            callback.run(res.toString());
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error in actionBtn onClick", t);
                    }
                }
            });
            bottomBar.addView(actionBtn, lp(MP, AndroidUtilities.dp(48)));

            root.addView(bottomBar, lp(MP, WC));

            setCustomView(root);
            updateActionButton();
            updateSelectAllBtn();
        }

        private void showWarningNotice(final String message) {
            if (message == null || message.length() == 0) return;
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (container != null) {
                            BulletinFactory.of(container, fragment != null ? fragment.getResourceProvider() : null)
                                    .createSimpleBulletin(message, "")
                                    .show();
                        } else if (fragment != null) {
                            BulletinFactory.of(fragment).createSimpleBulletin(message, "").show();
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error showing bulletin notice", t);
                    }
                }
            });
        }

        private static void styleCell(CheckBoxCell cell, BaseFragment f) {
            if (cell == null || f == null) return;
            try {
                boolean isDark = Theme.isCurrentThemeDark();
                int textColor = isDark ? 0xFFFFFFFF : 0xFF111111;
                cell.setTextColor(textColor);
                cell.setAlpha(1.0f);

                TextView tv = cell.getTextView();
                if (tv != null) {
                    tv.setAlpha(1.0f);
                    tv.setTextColor(textColor);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
                }
                if (cell.getAnimatedTextView() != null) {
                    cell.getAnimatedTextView().setAlpha(1.0f);
                    cell.getAnimatedTextView().setTextColor(textColor);
                    cell.getAnimatedTextView().setTypeface(Typeface.DEFAULT_BOLD);
                    cell.getAnimatedTextView().setTextSize(15);
                }
                if (cell.getValueTextView() != null) {
                    cell.getValueTextView().setAlpha(1.0f);
                    int valColor = isDark ? 0xFF64B5F6 : 0xFF1976D2;
                    cell.getValueTextView().setTextColor(valColor);
                    cell.getValueTextView().setTypeface(Typeface.DEFAULT_BOLD);
                    cell.getValueTextView().setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
                }

                cell.setCheckBoxColor(
                    Theme.key_checkbox,
                    Theme.key_checkbox,
                    Theme.key_checkboxCheck
                );
                if (cell.getCheckBoxRound() != null) {
                    cell.getCheckBoxRound().setAlpha(1.0f);
                    cell.getCheckBoxRound().setColor(
                        Theme.key_checkbox,
                        Theme.key_checkbox,
                        Theme.key_checkboxCheck
                    );
                    if (cell.getCheckBoxRound().getCheckBoxBase() != null) {
                        cell.getCheckBoxRound().getCheckBoxBase().setAlpha(1.0f);
                        cell.getCheckBoxRound().getCheckBoxBase().setBackgroundAlpha(1.0f);
                    }
                }
                cell.invalidate();
            } catch (Throwable ignored) {
            }
        }

        private void addInfoRow(Context ctx, LinearLayout root, String text, int colorOrKey, float sp, BaseFragment f) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextSize(sp);
            if (colorOrKey >= 0 && colorOrKey < 2000) {
                tv.setTextColor(Theme.getColor(colorOrKey, f.getResourceProvider()));
            } else {
                tv.setTextColor(colorOrKey);
            }
            tv.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), AndroidUtilities.dp(8));
            root.addView(tv, lp(MP, WC));
        }

        private View makeDivider(Context ctx, BaseFragment f) {
            View v = new View(ctx);
            v.setBackgroundColor(Theme.getColor(Theme.key_divider, f.getResourceProvider()));
            return v;
        }

        private void updateActionButton() {
            if (actionBtn != null) {
                String base = isExport ? s("export2") : s("import");
                actionBtn.setText(base + " (" + selected.size() + ")");
                actionBtn.setEnabled(!selected.isEmpty());
                actionBtn.setAlpha(selected.isEmpty() ? 0.45f : 1.0f);
            }
        }

        private void updateSelectAllBtn() {
            if (selectAllBtn != null) {
                if (selected.size() == items.size()) {
                    selectAllBtn.setText(s("deselect_all"));
                } else {
                    selectAllBtn.setText(s("select_all"));
                }
            }
        }

        // ─── List Adapter ────────────────────────────────────────────────────

        private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
            private final Context ctx;
            private final BaseFragment fragment;

            Adapter(Context ctx, BaseFragment fragment) {
                this.ctx = ctx;
                this.fragment = fragment;
            }

            @Override
            public VH onCreateViewHolder(ViewGroup parent, int viewType) {
                CheckBoxCell cell = new CheckBoxCell(ctx, 4, 21, fragment.getResourceProvider());
                cell.setLayoutParams(new RecyclerView.LayoutParams(MP, AndroidUtilities.dp(50)));
                cell.setBackground(Theme.getSelectorDrawable(false));
                cell.setAlpha(1.0f);
                styleCell(cell, fragment);
                return new VH(cell);
            }

            @Override
            public void onBindViewHolder(final VH holder, int pos) {
                ItemInfo item = items.get(pos);
                boolean last = (pos == items.size() - 1);
                boolean checked = selected.contains(item.id);
                holder.cell.setText(item.name, item.subtext, checked, !last);
                holder.cell.setChecked(checked, false);
                styleCell(holder.cell, fragment);
            }

            @Override
            public void onViewAttachedToWindow(VH holder) {
                super.onViewAttachedToWindow(holder);
                holder.cell.setAlpha(1.0f);
                styleCell(holder.cell, fragment);
            }

            @Override
            public int getItemCount() { return items.size(); }

            class VH extends RecyclerView.ViewHolder {
                final CheckBoxCell cell;
                VH(final CheckBoxCell cell) {
                    super(cell);
                    this.cell = cell;
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int pos = getAdapterPosition();
                            if (pos == RecyclerView.NO_POSITION || pos < 0 || pos >= items.size()) return;
                            ItemInfo item = items.get(pos);
                            boolean nowChecked;
                            if (selected.contains(item.id)) {
                                selected.remove(item.id);
                                nowChecked = false;
                            } else {
                                selected.add(item.id);
                                nowChecked = true;
                            }
                            cell.setChecked(nowChecked, true);
                            styleCell(cell, fragment);
                            updateActionButton();
                            updateSelectAllBtn();
                        }
                    });
                }
            }
        }
    }
}
