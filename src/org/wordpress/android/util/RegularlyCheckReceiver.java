package org.wordpress.android.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * RegularlyCheckReceiver
 * 
 * @author <a href="http://www.trinea.cn" target="_blank">Trinea</a> 2013-10-24
 */
public class RegularlyCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action;
        if (intent != null && (action = intent.getAction()) != null) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                RegularlyCheckService.printLog("boot complete receive");
                Intent i = new Intent(context, RegularlyCheckService.class);
                i.setAction(RegularlyCheckService.ACTION_BOOT_COMPLETED);
                context.startService(i);
            } else if (RegularlyCheckService.ACTION_CHECK.equals(action)) {
                RegularlyCheckService.printLog("receive check action");
                Intent i = new Intent(context, RegularlyCheckService.class);
                i.setAction(RegularlyCheckService.ACTION_CHECK);
                context.startService(i);
            }
        }
    }
}
