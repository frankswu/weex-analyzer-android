package com.taobao.weex.analyzer.view;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.taobao.weex.analyzer.core.AbstractLoopTask;
import com.taobao.weex.analyzer.core.MemoryChecker;
import com.taobao.weex.analyzer.utils.ViewUtils;
import com.taobao.weex.analyzer.view.chart.TimestampLabelFormatter;
import com.taobao.weex.analyzer.R;

/**
 * Description:
 * <p>
 * Created by rowandjj(chuyi)<br/>
 * Date: 16/10/17<br/>
 * Time: 下午3:45<br/>
 */

public class MemoryChartView extends DragSupportOverlayView {

    private DynamicChartViewController mChartViewController;
    private CheckMemoryTask mTask;

    private OnCloseListener mOnCloseListener;

    public MemoryChartView(Context application) {
        super(application);
        mWidth = WindowManager.LayoutParams.MATCH_PARENT;
        mHeight = (int) ViewUtils.dp2px(application, 150);
    }

    public void setOnCloseListener(@Nullable OnCloseListener listener){
        this.mOnCloseListener = listener;
    }

    @NonNull
    @Override
    protected View onCreateView() {
        //prepare chart view
        double maxMemory = MemoryChecker.maxMemory();
        double totalMemory = MemoryChecker.totalMemory();
        double maxY = Math.min(totalMemory * 2, maxMemory);
        mChartViewController = new DynamicChartViewController.Builder(mContext)
                .title(mContext.getResources().getString(R.string.wxt_memory))
                .titleOfAxisX(null)
                .titleOfAxisY("MB")
                .labelColor(Color.WHITE)
                .backgroundColor(Color.parseColor("#ba000000"))
                .lineColor(Color.parseColor("#00ff00"))
                .isFill(true)
                .fillColor(Color.parseColor("#ba00ff00"))
                .numXLabels(5)
                .minX(0)
                .maxX(20)
                .numYLabels(5)
                .minY(0)
                .maxY(ViewUtils.findSuitableVal(maxY,4))//step = verticalLabelsNum-1
                .labelFormatter(new TimestampLabelFormatter())
                .maxDataPoints(20 + 2)
                .build();

        FrameLayout frameLayout = new FrameLayout(mContext);
        View chartView = mChartViewController.getChartView();
        frameLayout.addView(chartView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        //add close btn. it's ugly here,we can expend chart view to support close btn.
        TextView closeBtn = new TextView(mContext);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setText(mContext.getResources().getString(R.string.wxt_close));
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mOnCloseListener != null && isViewAttached){
                    mOnCloseListener.close(MemoryChartView.this);
                }
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int) ViewUtils.dp2px(mContext, 50), (int) ViewUtils.dp2px(mContext, 30));
        params.gravity = Gravity.RIGHT;
        frameLayout.addView(closeBtn, params);

        return frameLayout;
    }


    @Override
    protected void onShown() {
        mTask = new CheckMemoryTask(mWholeView, mChartViewController);
        mTask.start();
    }

    @Override
    protected void onDismiss() {
        if (mTask != null) {
            mTask.stop();
            mTask = null;
            mChartViewController = null;
        }
    }

    private static class CheckMemoryTask extends AbstractLoopTask {

        private DynamicChartViewController mController;
        private int mAxisXValue = -1;
        private static final float LOAD_FACTOR = 0.75F;

        CheckMemoryTask(@NonNull View hostView, @NonNull DynamicChartViewController controller) {
            super(hostView);
            mDelayMillis = 1000;

            this.mController = controller;
        }

        @Override
        protected void onRun() {
            if (mController == null) {
                return;
            }
            mAxisXValue++;
            double memoryUsed = MemoryChecker.checkMemoryUsage();

            if (checkIfNeedUpdateYAxis(memoryUsed)) {
                mController.updateAxisY(0, (mController.getMaxY() - mController.getMinY()) * 2, 0);
            }
            mController.appendPointAndInvalidate(mAxisXValue, memoryUsed);
        }

        private boolean checkIfNeedUpdateYAxis(double memoryUsed) {
            double currentMaxY = (mController.getMaxY() - mController.getMinY());
            return currentMaxY * LOAD_FACTOR <= memoryUsed;
        }

        @Override
        protected void onStop() {
            mController = null;
            mHostView = null;
        }
    }

}












