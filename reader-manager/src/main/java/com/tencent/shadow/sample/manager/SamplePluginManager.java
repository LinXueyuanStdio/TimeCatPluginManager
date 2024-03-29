package com.tencent.shadow.sample.manager;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.tencent.shadow.core.manager.installplugin.InstalledPlugin;
import com.tencent.shadow.dynamic.host.EnterCallback;
import com.tencent.shadow.dynamic.loader.PluginServiceConnection;
import com.tencent.shadow.sample.plugin.IMyAidlInterface;
import com.timecat.identity.readonly.PluginHub;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SamplePluginManager extends FastPluginManager {

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private Context mCurrentContext;

    public SamplePluginManager(Context context) {
        super(context);
        mCurrentContext = context;
    }

    /**
     * @return PluginManager实现的别名，用于区分不同PluginManager实现的数据存储路径
     */
    @Override
    protected String getName() {
        return "PictureBed";
    }

    /**
     * @return demo插件so的abi
     */
    @Override
    public String getAbi() {
        return "";
    }

    /**
     * @return 宿主中注册的PluginProcessService实现的类名
     */
    @Override
    protected String getPluginProcessServiceName(String partKey) {
        return "com.timecat.module.plugin.container.MainPluginProcessService";
    }

    @Override
    public void enter(final Context context, long fromId, Bundle bundle, final EnterCallback callback) {
        if (fromId == PluginHub.FROM_ID_START_ACTIVITY) {
            onStartActivity(context, bundle, callback);
        } else if (fromId == PluginHub.FROM_ID_CALL_SERVICE) {
            callPluginService(context, bundle);
        } else {
            throw new IllegalArgumentException("不认识的fromId==" + fromId);
        }
    }

    private void onStartActivity(final Context context, Bundle bundle, final EnterCallback callback) {
        final String pluginZipPath = bundle.getString(PluginHub.KEY_PLUGIN_ZIP_PATH);
        final String partKey = bundle.getString(PluginHub.KEY_PLUGIN_PART_KEY);
        final String className = bundle.getString(PluginHub.KEY_CLASSNAME);
        final Bundle extras = bundle.getBundle(PluginHub.KEY_EXTRAS);

        if (className == null) {
            throw new NullPointerException("className == null");
        }

        if (callback != null) {
            final View view = LayoutInflater.from(mCurrentContext).inflate(R.layout.activity_load_plugin, null);
            callback.onShowLoadingView(view);
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InstalledPlugin installedPlugin = installPlugin(pluginZipPath, null, true);//这个调用是阻塞的
                    Intent pluginIntent = new Intent();
                    pluginIntent.setClassName(context.getPackageName(), className);
                    if (extras != null) {
                        pluginIntent.replaceExtras(extras);
                    }

                    startPluginActivity(installedPlugin, partKey, pluginIntent);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (callback != null) {
                    Handler uiHandler = new Handler(Looper.getMainLooper());
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onCloseLoadingView();
                            callback.onEnterComplete();
                        }
                    });
                }
            }
        });
    }

    private void callPluginService(final Context context, Bundle bundle) {
        final String pluginZipPath = bundle.getString(PluginHub.KEY_PLUGIN_ZIP_PATH);
        final String partKey = bundle.getString(PluginHub.KEY_PLUGIN_PART_KEY);
        final String className = bundle.getString(PluginHub.KEY_CLASSNAME);
        final Bundle extras = bundle.getBundle(PluginHub.KEY_EXTRAS);
        final String action = bundle.getString(PluginHub.KEY_ACTION);
        final Uri data = bundle.getParcelable(PluginHub.KEY_DATA);

        if (className == null) {
            throw new NullPointerException("className == null");
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InstalledPlugin installedPlugin = installPlugin(pluginZipPath, null, true);//这个调用是阻塞的
                    loadPluginWithApplicationCreated(installedPlugin.UUID, partKey);
                    Intent pluginIntent = new Intent();
                    pluginIntent.setClassName(context.getPackageName(), className);
                    if (extras != null) {
                        pluginIntent.replaceExtras(extras);
                    }
                    if (action != null) {
                        pluginIntent.setAction(action);
                    }
                    if (data != null) {
                        pluginIntent.setData(data);
                    }

                    boolean callSuccess = mPluginLoader.bindPluginService(pluginIntent, new PluginServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                            IMyAidlInterface iMyAidlInterface = IMyAidlInterface.Stub.asInterface(iBinder);
                            try {
                                String s = iMyAidlInterface.basicTypes(1, 2, true, 4.0f, 5.0, "6");
                                Log.i("SamplePluginManager", "iMyAidlInterface.basicTypes : " + s);
                            } catch (RemoteException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName componentName) {
                            throw new RuntimeException("onServiceDisconnected");
                        }
                    }, Service.BIND_AUTO_CREATE);

                    if (!callSuccess) {
                        throw new RuntimeException("bind service失败 className==" + className);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
