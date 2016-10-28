package com.taobao.weex.analyzer.view;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.taobao.weex.analyzer.R;
import com.taobao.weex.analyzer.core.LogcatDumpBuilder;
import com.taobao.weex.analyzer.core.LogcatDumper;
import com.taobao.weex.analyzer.utils.ViewUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LogView extends DragSupportOverlayView {

    private LogcatDumper mLogcatDumper;

    private OnCloseListener mOnCloseListener;
    private OnLogConfigChangedListener mConfigChangeListener;
    private onStatusChangedListener mCollapseListener;

    private SimpleOverlayView mCollapsedView;

    private static final Map<String, LogcatDumper.Rule> sDefaultRules;

    private static final String FILTER_JS_LOG = "jsLog";
    private static final String FILTER_CALL_NATIVE = "callNative";
    private static final String FILTER_CALL_JS = "callJS";
    private static final String FILTER_ALL = "all";
    private static final String FILTER_EXCEPTION = "reportJSException";

    @IntDef({Size.SMALL, Size.MEDIUM, Size.LARGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Size {
        int SMALL = 0;
        int MEDIUM = 1;
        int LARGE = 2;
    }

    private int mLogLevel;
    private String mFilterName;
    private int mViewSize = Size.MEDIUM;

    private LogListAdapter adapter;

    private boolean isSettingOpend;


    static {
        sDefaultRules = new HashMap<>();
        sDefaultRules.put(FILTER_JS_LOG, new LogcatDumper.Rule(FILTER_JS_LOG, "jsLog"));
        sDefaultRules.put(FILTER_CALL_NATIVE, new LogcatDumper.Rule(FILTER_CALL_NATIVE, "callNative"));
        sDefaultRules.put(FILTER_CALL_JS, new LogcatDumper.Rule(FILTER_CALL_JS, "callJS"));
        sDefaultRules.put(FILTER_ALL, new LogcatDumper.Rule(FILTER_ALL, null));
        sDefaultRules.put(FILTER_EXCEPTION, new LogcatDumper.Rule(FILTER_EXCEPTION, "reportJSException"));
    }


    public interface OnLogConfigChangedListener {
        void onLogLevelChanged(int level);

        void onLogFilterChanged(String filterName);

        void onLogSizeChanged(@Size int size);
    }

    public interface onStatusChangedListener {
        void onCollapsed();

        void onExpanded();
    }

    public LogView(Context application) {
        super(application);
        mWidth = WindowManager.LayoutParams.MATCH_PARENT;
    }

    public void setOnCloseListener(@Nullable OnCloseListener listener) {
        this.mOnCloseListener = listener;
    }

    public void setOnLogConfigChangedListener(@Nullable OnLogConfigChangedListener listener) {
        this.mConfigChangeListener = listener;
    }

    public void setOnCollapseListener(@Nullable onStatusChangedListener listener) {
        this.mCollapseListener = listener;
    }

    public void setLogLevel(int level) {
        this.mLogLevel = level;
    }

    public void setFilterName(String filterName) {
        this.mFilterName = filterName;
    }

    public void setViewSize(@Size int size) {
        this.mViewSize = size;
    }


    @NonNull
    @Override
    protected View onCreateView() {
        View wholeView = View.inflate(mContext, R.layout.log_view, null);

        final View hold = wholeView.findViewById(R.id.hold);
        View clear = wholeView.findViewById(R.id.clear);
        View close = wholeView.findViewById(R.id.close);
//        View search = wholeView.findViewById(R.id.search);

        RadioGroup levelGroup = (RadioGroup) wholeView.findViewById(R.id.level_group);
        RadioGroup ruleGroup = (RadioGroup) wholeView.findViewById(R.id.rule_group);
        RadioGroup sizeGroup = (RadioGroup) wholeView.findViewById(R.id.height_group);

        final TextView settings = (TextView) wholeView.findViewById(R.id.settings);
        View collapse = wholeView.findViewById(R.id.collapse);

        final ViewGroup settingContent = (ViewGroup) wholeView.findViewById(R.id.setting_content);

        final RecyclerView logList = (RecyclerView) wholeView.findViewById(R.id.list);


        //setting init
        settings.setText(String.format(Locale.CHINA, mContext.getString(R.string.wxt_settings), "(off)"));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSettingOpend = !isSettingOpend;
                if (isSettingOpend) {
                    settings.setText(String.format(Locale.CHINA, mContext.getString(R.string.wxt_settings), "(on)"));
                    settingContent.setVisibility(View.VISIBLE);
                } else {
                    settings.setText(String.format(Locale.CHINA, mContext.getString(R.string.wxt_settings), "(off)"));
                    settingContent.setVisibility(View.GONE);
                }
            }
        });

        collapse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performCollapse();
            }
        });

        //view size

        setLogSize(mViewSize, logList, false);
        switch (mViewSize) {
            case Size.SMALL:
                ((RadioButton) wholeView.findViewById(R.id.height_small)).setChecked(true);
                break;
            case Size.MEDIUM:
                ((RadioButton) wholeView.findViewById(R.id.height_medium)).setChecked(true);
                break;
            case Size.LARGE:
                ((RadioButton) wholeView.findViewById(R.id.height_large)).setChecked(true);
                break;
        }


        sizeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.height_small) {
                    mViewSize = Size.SMALL;
                } else if (checkedId == R.id.height_medium) {
                    mViewSize = Size.MEDIUM;
                } else if (checkedId == R.id.height_large) {
                    mViewSize = Size.LARGE;
                }
                setLogSize(mViewSize, logList, true);
            }
        });

//        search.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//            }
//        });

        //init recyclerView
        logList.setLayoutManager(new LinearLayoutManager(mContext));
        adapter = new LogListAdapter(mContext, logList);
        logList.setAdapter(adapter);


        switch (mLogLevel) {
            case Log.VERBOSE:
                ((RadioButton) wholeView.findViewById(R.id.level_v)).setChecked(true);
                break;
            case Log.INFO:
                ((RadioButton) wholeView.findViewById(R.id.level_i)).setChecked(true);
                break;
            case Log.DEBUG:
                ((RadioButton) wholeView.findViewById(R.id.level_d)).setChecked(true);
                break;
            case Log.WARN:
                ((RadioButton) wholeView.findViewById(R.id.level_w)).setChecked(true);
                break;
            case Log.ERROR:
                ((RadioButton) wholeView.findViewById(R.id.level_e)).setChecked(true);
                break;
        }

        if (mFilterName == null) {
            mFilterName = FILTER_ALL;
        }

        switch (mFilterName) {
            case FILTER_ALL:
                ((RadioButton) wholeView.findViewById(R.id.rule_all)).setChecked(true);
                break;
            case FILTER_CALL_JS:
                ((RadioButton) wholeView.findViewById(R.id.rule_calljs)).setChecked(true);
                break;
            case FILTER_CALL_NATIVE:
                ((RadioButton) wholeView.findViewById(R.id.rule_callnative)).setChecked(true);
                break;
            case FILTER_JS_LOG:
                ((RadioButton) wholeView.findViewById(R.id.rule_jslog)).setChecked(true);
                break;
            case FILTER_EXCEPTION:
                ((RadioButton) wholeView.findViewById(R.id.rule_exception)).setChecked(true);
                break;
        }


        levelGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (mLogcatDumper == null) {
                    return;
                }

                if (adapter != null) {
                    adapter.clear();
                }

                int level = mLogLevel;
                if (checkedId == R.id.level_i) {
                    level = Log.INFO;
                } else if (checkedId == R.id.level_v) {
                    level = Log.VERBOSE;
                } else if (checkedId == R.id.level_d) {
                    level = Log.DEBUG;
                } else if (checkedId == R.id.level_e) {
                    level = Log.ERROR;
                } else if (checkedId == R.id.level_w) {
                    level = Log.WARN;
                }

                if(level != mLogLevel){
                    mLogLevel = level;
                    mLogcatDumper.setLevel(mLogLevel);
                    if (mConfigChangeListener != null) {
                        mConfigChangeListener.onLogLevelChanged(mLogLevel);
                    }
                }


                // history cached log will be filtered by new rules
                mLogcatDumper.findCachedLogByNewFilters();
            }
        });

        ruleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (mLogcatDumper == null) {
                    return;
                }

                if (adapter != null) {
                    adapter.clear();
                }

                mLogcatDumper.removeAllRule();
                String filterName = mFilterName;
                if (checkedId == R.id.rule_all) {
                    filterName = FILTER_ALL;
                } else if (checkedId == R.id.rule_jslog) {
                    filterName = FILTER_JS_LOG;
                    mLogcatDumper.addRule(sDefaultRules.get(FILTER_JS_LOG));
                } else if (checkedId == R.id.rule_calljs) {
                    filterName = FILTER_CALL_JS;
                    mLogcatDumper.addRule(sDefaultRules.get(FILTER_CALL_JS));
                } else if (checkedId == R.id.rule_callnative) {
                    filterName = FILTER_CALL_NATIVE;
                    mLogcatDumper.addRule(sDefaultRules.get(FILTER_CALL_NATIVE));
                } else if (checkedId == R.id.rule_exception) {
                    filterName = FILTER_EXCEPTION;
                    mLogcatDumper.addRule(sDefaultRules.get(FILTER_EXCEPTION));
                }

                if(!filterName.equals(mFilterName)){
                    mFilterName = filterName;
                    if (mConfigChangeListener != null) {
                        mConfigChangeListener.onLogFilterChanged(mFilterName);
                    }
                }

                mLogcatDumper.findCachedLogByNewFilters();
            }
        });


        hold.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (adapter == null) {
                    return;
                }

                if (isViewAttached) {
                    if (adapter.isHoldModeEnabled()) {
                        adapter.setHoldModeEnabled(false);
                        ((TextView) hold).setText("hold(off)");
                    } else {
                        adapter.setHoldModeEnabled(true);
                        ((TextView) hold).setText("hold(on)");
                    }
                }
            }
        });

        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isViewAttached && adapter != null) {
                    adapter.clear();
                    //maybe we need clear cache here
                    if (mLogcatDumper != null) {
                        mLogcatDumper.clearCachedLog();
                    }
                }
            }
        });

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isViewAttached && mOnCloseListener != null) {
                    mOnCloseListener.close(LogView.this);
                }
            }
        });

        return wholeView;
    }

    @Override
    protected void onShown() {
        mLogcatDumper = new LogcatDumpBuilder()
                .listener(new LogcatDumper.OnLogReceivedListener() {
                    @Override
                    public void onReceived(@NonNull List<LogcatDumper.LogInfo> logList) {
                        if (adapter != null) {
                            adapter.addLog(logList);
                        }
                    }
                })
                .level(mLogLevel)
                .enableCache(true)
                .cacheLimit(1000)
                .build();

        LogcatDumper.Rule rule = null;
        if (mFilterName != null) {
            rule = sDefaultRules.get(mFilterName);
        }

        if (rule != null) {
            mLogcatDumper.addRule(rule);
        }

        mLogcatDumper.beginDump();
    }

    @Override
    protected void onDismiss() {
        if(mLogcatDumper != null){
            mLogcatDumper.destroy();
            mLogcatDumper = null;
        }
    }

    @Override
    protected void onDestroy() {
        if(mCollapsedView != null){
            mCollapsedView.dismiss();
//            mCollapsedView = null;
        }
    }

    private void performCollapse() {
        //callback
        if (mCollapseListener != null) {
            mCollapseListener.onCollapsed();
        }

        //dismiss current view
        dismiss();

        //show collapse view
        if(mCollapsedView == null){
            mCollapsedView = new SimpleOverlayView(mContext, "Log");
            mCollapsedView.setOnClickListener(new SimpleOverlayView.OnClickListener() {
                @Override
                public void onClick(@NonNull IOverlayView view) {
                    mCollapsedView.dismiss();
                    if (mCollapseListener != null) {
                        mCollapseListener.onExpanded();
                    }
                    LogView.this.show();
                }
            });
        }
        mCollapsedView.show();
    }


    private void setLogSize(@Size int size, RecyclerView logList, boolean allowFireEvent) {
        if (logList == null) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) logList.getLayoutParams();
        if (params == null) {
            return;
        }

        //hard code here..
        final int heightSmall = (int) ViewUtils.dp2px(mContext, 200);
        final int heightMedium = (int) ViewUtils.dp2px(mContext, 350);
        final int heightLarge = FrameLayout.LayoutParams.MATCH_PARENT;

        int height = params.height;
        switch (size) {
            case Size.SMALL:
                height = heightSmall;
                break;
            case Size.MEDIUM:
                height = heightMedium;
                break;
            case Size.LARGE:
                height = heightLarge;
                break;
        }
        if (height != params.height) {
            params.height = height;
            logList.setLayoutParams(params);
            if (mConfigChangeListener != null && allowFireEvent) {
                mConfigChangeListener.onLogSizeChanged(mViewSize);
            }
        }
    }


    private static class LogListAdapter extends RecyclerView.Adapter {

        private List<LogcatDumper.LogInfo> mLogData;
        private Context mContext;

        private boolean isHoldMode = false;

        private RecyclerView mList;

        LogListAdapter(@NonNull Context context, @NonNull RecyclerView list) {
            this.mContext = context;
            mLogData = new ArrayList<>();
            this.mList = list;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_log, parent, false);
            return new LogViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof LogViewHolder) {
                ((LogViewHolder) holder).bind(mLogData.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return mLogData.size();
        }

        void addLog(@NonNull LogcatDumper.LogInfo log) {
            mLogData.add(log);
            notifyItemInserted(mLogData.size());
        }

        void addLog(@NonNull List<LogcatDumper.LogInfo> list) {
            if (list.size() == 1) {
                addLog(list.get(0));
            } else {
                int size = mLogData.size();
                mLogData.addAll(list);
                notifyItemRangeInserted(size, list.size());
            }

            if (!isHoldMode) {
                try {
                    mList.smoothScrollToPosition(this.getItemCount() - 1);
                } catch (Exception e) {
                    //ignored
                }
            }
        }

        void clear() {
            mLogData.clear();
            notifyDataSetChanged();
        }

        void setHoldModeEnabled(boolean enabled) {
            this.isHoldMode = enabled;
        }

        boolean isHoldModeEnabled() {
            return this.isHoldMode;
        }
    }


    private static class LogViewHolder extends RecyclerView.ViewHolder {

        private TextView mText;

        LogViewHolder(View itemView) {
            super(itemView);
            mText = (TextView) itemView.findViewById(R.id.text_log);
        }

        void bind(LogcatDumper.LogInfo log) {
            switch (log.level) {
                case Log.VERBOSE:
                    mText.setTextColor(Color.parseColor("#FFFFFF"));
                    break;
                case Log.INFO:
                    mText.setTextColor(Color.parseColor("#2196F3"));

                    break;
                case Log.DEBUG:
                    mText.setTextColor(Color.parseColor("#4CAF50"));

                    break;
                case Log.WARN:
                    mText.setTextColor(Color.parseColor("#FFEB3B"));
                    break;
                case Log.ERROR:
                    mText.setTextColor(Color.parseColor("#F44336"));
                    break;
                default:
                    mText.setTextColor(Color.parseColor("#FFFFFF"));

            }
            mText.setText(log.message);
        }
    }

}