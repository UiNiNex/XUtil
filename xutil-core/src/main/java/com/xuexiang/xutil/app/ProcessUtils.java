/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xutil.app;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;

import com.xuexiang.xutil.XUtil;
import com.xuexiang.xutil.common.logger.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <pre>
 *     desc   : 进程相关工具类
 *     author : xuexiang
 *     time   : 2018/4/28 上午12:35
 * </pre>
 */
public final class ProcessUtils {

    private ProcessUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    /**
     * 获取前台线程包名
     * <p>当不是查看当前 App，且 SDK 大于 21 时，
     * 需添加权限
     * {@code <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />}</p>
     *
     * @return 前台应用包名
     */
    public static String getForegroundProcessName() {
        ActivityManager manager =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return null;
        List<ActivityManager.RunningAppProcessInfo> pInfo = manager.getRunningAppProcesses();
        if (pInfo != null && pInfo.size() > 0) {
            for (ActivityManager.RunningAppProcessInfo aInfo : pInfo) {
                if (aInfo.importance
                        == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return aInfo.processName;
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {
            PackageManager packageManager = XUtil.getContext().getPackageManager();
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            List<ResolveInfo> list =
                    packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            Log.i("ProcessUtils", list.toString());
            if (list.size() <= 0) {
                Log.i("ProcessUtils",
                        "getForegroundProcessName() called" + ": 无\"有权查看使用权限的应用\"选项");
                return null;
            }
            try {// 有"有权查看使用权限的应用"选项
                ApplicationInfo info =
                        packageManager.getApplicationInfo(XUtil.getContext().getPackageName(), 0);
                AppOpsManager aom =
                        (AppOpsManager) XUtil.getContext().getSystemService(Context.APP_OPS_SERVICE);
                if (aom != null) {
                    if (aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                            info.uid,
                            info.packageName) != AppOpsManager.MODE_ALLOWED) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        XUtil.getContext().startActivity(intent);
                    }
                    if (aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                            info.uid,
                            info.packageName) != AppOpsManager.MODE_ALLOWED) {
                        Log.i("ProcessUtils", "没有打开\"有权查看使用权限的应用\"选项");
                        return null;
                    }
                }
                UsageStatsManager usageStatsManager = (UsageStatsManager) XUtil.getContext()
                        .getSystemService(Context.USAGE_STATS_SERVICE);
                List<UsageStats> usageStatsList = null;
                if (usageStatsManager != null) {
                    long endTime = System.currentTimeMillis();
                    long beginTime = endTime - 86400000 * 7;
                    usageStatsList = usageStatsManager
                            .queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                                    beginTime, endTime);
                }
                if (usageStatsList == null || usageStatsList.isEmpty()) return null;
                UsageStats recentStats = null;
                for (UsageStats usageStats : usageStatsList) {
                    if (recentStats == null
                            || usageStats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                        recentStats = usageStats;
                    }
                }
                return recentStats == null ? null : recentStats.getPackageName();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 获取后台服务进程
     * <p>需添加权限
     * {@code <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />}</p>
     *
     * @return 后台服务进程
     */
    public static Set<String> getAllBackgroundProcesses() {
        ActivityManager am =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return Collections.emptySet();
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        Set<String> set = new HashSet<>();
        if (info != null) {
            for (ActivityManager.RunningAppProcessInfo aInfo : info) {
                Collections.addAll(set, aInfo.pkgList);
            }
        }
        return set;
    }

    /**
     * 杀死所有的后台服务进程
     * <p>需添加权限
     * {@code <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />}</p>
     *
     * @return 被暂时杀死的服务集合
     */
    @SuppressLint("MissingPermission")
    public static Set<String> killAllBackgroundProcesses() {
        ActivityManager am =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return Collections.emptySet();
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        Set<String> set = new HashSet<>();
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            for (String pkg : aInfo.pkgList) {
                am.killBackgroundProcesses(pkg);
                set.add(pkg);
            }
        }
        info = am.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            for (String pkg : aInfo.pkgList) {
                set.remove(pkg);
            }
        }
        return set;
    }

    /**
     * 杀死后台服务进程
     * <p>需添加权限
     * {@code <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />}</p>
     *
     * @param packageName 包名
     * @return {@code true}: 杀死成功<br>{@code false}: 杀死失败
     */
    @SuppressLint("MissingPermission")
    public static boolean killBackgroundProcesses(@NonNull final String packageName) {
        ActivityManager am =
                (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
        if (info == null || info.size() == 0) return true;
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            if (Arrays.asList(aInfo.pkgList).contains(packageName)) {
                Logger.d("[killBackgroundProcesses]：" + aInfo.processName);
                am.killBackgroundProcesses(packageName);
            }
        }
        info = am.getRunningAppProcesses();
        if (info == null || info.size() == 0) return true;
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            if (Arrays.asList(aInfo.pkgList).contains(packageName)) {
                return false;
            }
        }
        return true;
    }


    /**
     * 清理后台进程与服务
     *
     * @return 被清理的数量
     */
    public static int gc() {
        long beforeGCDeviceUsableMemory = getDeviceUsableMemory();
        int count = 0; // 清理掉的进程数
        ActivityManager am = (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        // 获取正在运行的service列表
        List<ActivityManager.RunningServiceInfo> serviceList = am.getRunningServices(100);
        if (serviceList != null)
            for (ActivityManager.RunningServiceInfo service : serviceList) {
                if (service.pid == android.os.Process.myPid())
                    continue;
                try {
                    android.os.Process.killProcess(service.pid);
                    count++;
                } catch (Exception e) {
                    e.getStackTrace();
                }
            }

        // 获取正在运行的进程列表
        List<ActivityManager.RunningAppProcessInfo> processList = am.getRunningAppProcesses();
        if (processList != null)
            for (ActivityManager.RunningAppProcessInfo process : processList) {
                // 一般数值大于RunningAppProcessInfo.IMPORTANCE_SERVICE的进程都长时间没用或者空进程了
                // 一般数值大于RunningAppProcessInfo.IMPORTANCE_VISIBLE的进程都是非可见进程，也就是在后台运行着
                if (process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    // pkgList 得到该进程下运行的包名
                    String[] pkgList = process.pkgList;
                    for (String pkgName : pkgList) {
                        Logger.d("======正在杀死包名：" + pkgName);
                        try {
                            am.killBackgroundProcesses(pkgName);
                            count++;
                        } catch (Exception e) { // 防止意外发生
                            e.getStackTrace();
                        }
                    }
                }
            }
        Logger.d("清理了" + (getDeviceUsableMemory() - beforeGCDeviceUsableMemory) + "M内存");
        return count;
    }

    /**
     * 获取设备的可用内存大小
     * @return 当前内存大小
     */
    public static int getDeviceUsableMemory() {
        ActivityManager am = (ActivityManager) XUtil.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        // 返回当前系统的可用内存
        return (int) (mi.availMem / (1024 * 1024));
    }
}