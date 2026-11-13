package org.telegram.messenger;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import org.telegram.messenger.regular.BuildConfig;
import org.telegram.messenger.regular.UltraTelUpdater;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                if ("org.telegram.ui.LaunchActivity".equals(activity.getClass().getName())) {
                    UltraTelUpdater.check(activity, false);
                }
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }
}
