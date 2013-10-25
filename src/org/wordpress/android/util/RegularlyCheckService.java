package org.wordpress.android.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.xmlrpc.android.ApiHelper;
import org.xmlrpc.android.XMLRPCException;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.ui.comments.CommentsActivity;

/**
 * RegularlyCheckService
 * 
 * @author <a href="http://www.trinea.cn" target="_blank">Trinea</a> 2013-10-24
 */
public class RegularlyCheckService extends Service {

    public static final String        TAG                    = "RegularlyCheckService";
    public static final String        LOG_SUFFIX             = "get recent comment regularly, ";
    public static final int           MINUTE_TO_MILLS        = 60 * 1000;

    /** action when boot completed **/
    public final static String        ACTION_BOOT_COMPLETED  = "cn.trinea.action.ACTION_BOOT_COMPLETED";
    /** action which represents need check now **/
    public final static String        ACTION_CHECK           = "cn.trinea.action.ACTION_CHECK";
    /** action which represents change interval **/
    public final static String        ACTION_CHANGE_INTERVAL = "cn.trinea.action.ACTION_CHANGE_INTERVAL";

    private Context                   context;

    private List<Map<String, Object>> oldCommentList;
    private GetRecentCommentsTask     getCommentsTask;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action;
        printLog("start service");
        if (intent != null && (action = intent.getAction()) != null) {
            if (ACTION_BOOT_COMPLETED.equals(action) || ACTION_CHANGE_INTERVAL.equals(action)) {
                setAlarmToRegularlyCheck();
            } else if (ACTION_CHECK.equals(action)) {
                if (isNetworkAvailable()) {
                    getRecentComments(1);
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * whether network is meet the requirements
     * 
     * @return
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isAvailable()) {
            return false;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (pref == null
            || pref.getBoolean("wp_pref_comment_setting_is_wifi",
                               getResources().getBoolean(R.bool.comment_setting_is_wifi))) {
            NetworkInfo wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifiNetworkInfo == null || State.CONNECTED != wifiNetworkInfo.getState()) {
                return false;
            }
        }
        return true;
    }

    /**
     * set alarm to regualrly check
     */
    private void setAlarmToRegularlyCheck() {
        Intent intent = new Intent(this, RegularlyCheckReceiver.class);
        intent.setAction(ACTION_CHECK);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        int defaultInterval = getResources().getInteger(R.integer.comment_setting_interval);
        long intervalMinute = defaultInterval;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (pref != null) {
            try {
                intervalMinute = Integer.parseInt(pref.getString("wp_pref_comment_setting_interval", ""));
            } catch (Exception e) {
            }
        }

        AlarmManager alarm = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, 10 * 1000, intervalMinute * MINUTE_TO_MILLS, pendingIntent);
        printLog("begin regularly runnable, interval is " + intervalMinute + " minutes");
    }

    /**
     * get recent comments
     * 
     * @param number
     */
    public void getRecentComments(int number) {
        Map<String, Object> hPost = new HashMap<String, Object>();
        hPost.put("number", number);

        if (WordPress.wpDB == null) {
            printLog("WordPress.wpDB is null");
            return;
        }
        if (WordPress.currentBlog == null) {
            WordPress.getCurrentBlog();
            if (WordPress.currentBlog == null) {
                printLog("WordPress.currentBlog is null");
                return;
            }
        }

        Object[] params = { WordPress.currentBlog.getBlogId(), WordPress.currentBlog.getUsername(),
                WordPress.currentBlog.getPassword(), hPost };
        if (getCommentsTask == null || getCommentsTask.getStatus() != Status.RUNNING) {
            oldCommentList = WordPress.wpDB.loadComments(WordPress.currentBlog.getId());
            getCommentsTask = new GetRecentCommentsTask();
            getCommentsTask.execute(params);
        }
    }

    class GetRecentCommentsTask extends AsyncTask<Object, Void, Map<Integer, Map<?, ?>>> {

        protected void onPostExecute(Map<Integer, Map<?, ?>> newCommentList) {

            printLog("finish");
            getCommentsTask = null;
            if (newCommentList == null || newCommentList.size() == 0) {
                return;
            }
            try {
                Set<Integer> keySet = newCommentList.keySet();
                if (keySet == null || keySet.size() == 0) {
                    return;
                }

                int lastCommentId = 0;
                String commentAuthor = null, commentContent = null, commentBlog = null;
                for (Integer commentId : keySet) {
                    lastCommentId = commentId;
                }
                Map<?, ?> newContent = newCommentList.get(lastCommentId);
                if (newContent != null) {
                    commentContent = (String)newContent.get("content");
                    commentAuthor = (String)newContent.get("author");
                    commentBlog = (String)newContent.get("post_title");
                }

                // if exist new comment, notification
                boolean isNewComment = true;
                int oldCommentListSize = oldCommentList.size();
                if (oldCommentList != null && oldCommentListSize > 0) {
                    for (int i = 0; i < oldCommentListSize; i++) {
                        Map<String, Object> contentHash = oldCommentList.get(i);
                        if (contentHash != null && (Integer)contentHash.get("commentID") == lastCommentId) {
                            isNewComment = false;
                            break;
                        }
                    }
                }
                if (isNewComment) {
                    printLog("has new comment");
                    newCommentNotification(commentAuthor, commentContent, commentBlog);
                } else {
                    printLog("not has new comment");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Map<Integer, Map<?, ?>> doInBackground(Object... args) {

            printLog("begin…");
            Map<Integer, Map<?, ?>> commentsResult;
            try {
                commentsResult = ApiHelper.refreshComments(context.getApplicationContext(), args);
            } catch (XMLRPCException e) {
                return null;
            }
            return commentsResult;
        }

    }

    private void newCommentNotification(String commentAuthor, String commentContent, String commentBlog) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.app_icon)
                                                                                 .setContentTitle(String.format(getString(R.string.comment_noti_title),
                                                                                                                commentAuthor,
                                                                                                                commentBlog))
                                                                                 .setContentText(commentContent)
                                                                                 .setTicker(getString(R.string.comment_noti_ticker))
                                                                                 .setAutoCancel(true)
                                                                                 .setVibrate(new long[] { 0, 2 })
                                                                                 .setLights(0xff00ff00, 300, 100);
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(context, CommentsActivity.class);
        Bundle extras = new Bundle();
        extras.putBoolean("fromNotification", true);
        if (WordPress.currentBlog != null) {
            extras.putInt("id", WordPress.currentBlog.getId());
        }
        intent.putExtras(extras);
        builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        notificationManager.notify(0, builder.build());
    }

    public static void printLog(String content) {
        Log.i(TAG, new StringBuilder(128).append(LOG_SUFFIX).append(content).toString());
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
}
